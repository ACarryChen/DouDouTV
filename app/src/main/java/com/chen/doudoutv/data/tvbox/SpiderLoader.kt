package com.chen.doudoutv.data.tvbox

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * TVBox type 3 爬虫 JAR 运行时加载器（进程级单例）。csp_XxxGuard 是加密壳：
 * 壳 JAR 含壳 dex + 解密用 so + 加密 dex。三个硬约束：
 *  1. 整个 JAR（zip）交 DexClassLoader——Init 要从自己容器里读 so/guard 资源，纯 dex 读出来是 null；
 *  2. Init.init 会解 so + native 解密出真 dex，主 loader 必须用「jar + 扫描到的解密 dex」自己组；
 *  3. 进程级单例——每条线路 new 一个 TvBoxClient，实例级缓存会导致重复下载/重复壳 Init/
 *     重复起代理，正是「每点一次都弹『正在初始化本地代理』」的根因。
 */
object SpiderLoader {

    /** 已初始化的 Application Context（init 幂等，进程内只跑一次） */
    @Volatile
    private var appContext: Context? = null

    private lateinit var jarDir: File
    private lateinit var optDir: File

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /** key → 可加载 Guard 的 classloader（由 spider jar + 扫到的解密 dex 自己组） */
    private val loaders = ConcurrentHashMap<String, ClassLoader>()

    /**
     * key → 壳 Init.loader() 返回的 classloader，仅作补充尝试、不当主 loader。
     *
     * 真机证据：新牛二壳 loader() 返回的 DexClassLoader 里只有一个
     * code_cache/sharedb/config.db，没有解密出来的爬虫代码，拿它当主 loader
     * 会直接 ClassNotFoundException、播放整条链路失败。
     */
    private val shellLoaders = ConcurrentHashMap<String, ClassLoader>()

    /** key → 这个包跑壳 Init 的时刻：解密 dex 的新鲜度按它算，不能写死窗口 */
    private val initStartedAt = ConcurrentHashMap<String, Long>()

    private val instances = ConcurrentHashMap<String, SpiderProxy>()

    /** key → jar 路径 */
    private val jars = ConcurrentHashMap<String, String>()

    /** key → 已确认的初始化失败原因；命中后直接报错，避免重复触发壳初始化（重复弹「正在初始化本地代理」） */
    private val failedInit = ConcurrentHashMap<String, String>()

    /** 已启动过 LocalServer 的 loader key 集合（进程级，避免重复 start 抛端口占用） */
    private val localServerStarted = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** 壳初始化专用线程：Init.init 是纯阻塞反射调用，协程取消对纯阻塞无效，只能放独立线程上用超时等待 */
    private val initExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "spider-init").apply { isDaemon = true }
    }

    /** Guard 类加载/实例化专用线程：loadClass 触发 Guard 类 static{}，newInstance 触发实例构造，
     *  壳可能把代理/JSVM 初始化塞进这些地方导致无限阻塞，同样只能靠独立线程 + 超时兜底 */
    private val classExec: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "spider-class").apply { isDaemon = true }
    }

    /** 最近一次失败原因，供上层把真实错误透出到 UI */
    @Volatile
    var lastError: String = ""
        private set

    private const val TAG = "SpiderLoader"
    private val DEX_MAGIC = byteArrayOf(0x64, 0x65, 0x78, 0x0A) // "dex\n"
    private const val INIT_TIMEOUT_MS = 60_000L
    /** Guard 类 loadClass/newInstance 的超时：壳的 Guard 类 static{} 可能无限阻塞，
     *  这两个调用触发它时必须由超时兜底，否则整个流程挂死 */
    private const val CLASS_TIMEOUT_MS = 30_000L
    /** 壳 Init 内部会回连本地代理（127.0.0.1:8964 起递增端口）确认就绪，
     *  代理未起时抛 ConnectException。策略：第 1 次 Init 失败后轮询等代理端口
     *  出现，端口通了再调第 2 次 Init。最多 2 次 Init，避免反复弹「正在初始化本地代理」。 */
    private const val PROXY_WAIT_MS = 30_000L
    private const val PROXY_WAIT_INTERVAL_MS = 2_000L

    /** 兜底扫描的端口带：先扫实测到的壳代理带（8964 起递增），再扫随机端口大区 */
    private val FALLBACK_PORT_BANDS = listOf(8964..9978, 20000..50000)

    /** 单次兜底扫描的用时上限：轮询间隔 2 秒，留足余量给端口探测之外的环节 */
    private const val FALLBACK_SCAN_BUDGET_MS = 1_200L

    /** 进程级初始化：幂等，多次调用（多个 TvBoxClient）只跑一次。 */
    fun init(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            val app = context.applicationContext
            jarDir = File(app.cacheDir, "spiders").also { it.mkdirs() }
            optDir = File(app.cacheDir, "spider_opt").also { it.mkdirs() }
            appContext = app
        }
    }

    /** 已初始化的 Context；未 init 就调用加载方法是内部错误 */
    private fun ctx(): Context = appContext
        ?: throw IllegalStateException("SpiderLoader 尚未初始化（需要 Context 才能加载爬虫）")

    private fun parseSpider(raw: String): Pair<String, String> {
        val parts = raw.trim().split(';').map { it.trim() }
        return parts.firstOrNull().orEmpty() to parts.getOrNull(2).orEmpty()
    }

    /** 只查缓存：返回 spiderRaw 对应的 loader key（已加载过）；从未加载返回 null。
     *  不会触发 ensureLoaded，不会下载 JAR，不会起 Go 代理。供远程搜索等轻量场景用。 */
    fun cachedLoaderKeyOrNull(spiderRaw: String): String? {
        val (url, md5) = parseSpider(spiderRaw)
        val key = md5.ifBlank { md5Hex(url) }
        return if (loaders.containsKey(key)) key else null
    }

    /**
     * 同一个 spider 包同时只允许跑一个加载流程。
     *
     * 启动预热和首页刷新会各自走到这里（两条链路都持有自己的 TvBoxClient 实例，
     * 实例级的 configSpiderUrl 互不知情）。并发跑两遍壳 Init 的后果不是浪费时间那么简单：
     * 壳会重复起本地代理、重复弹「正在初始化本地代理」，正是这套加载要消灭的症状。
     * 缓存检查因此必须挪进锁内，否则后进入的那个会重复执行 prepareLoader。
     */
    private val loadMutex = Mutex()

    suspend fun ensureLoaded(spiderRaw: String): String = withContext(Dispatchers.IO) {
        val (url, md5) = parseSpider(spiderRaw)
        check(url.isNotBlank()) { "配置里没有 spider 字段（此类配置需要 spider 包才能解析）" }
        loadMutex.withLock { ensureLoadedLocked(md5.ifBlank { md5Hex(url) }, url, md5) }
    }

    private suspend fun ensureLoadedLocked(key: String, url: String, md5: String): String {
        loaders[key]?.let {
            // 缓存命中：spider 已加载，但 LocalServer 可能未启动（进程重启后缓存还在但本地代理已死）。
            // LocalServer 是端口 9978 的视频代理，云盘播放依赖它，必须确保启动。
            if (localServerStarted.add(key)) ensureLocalProxy(key, it)
            return key
        }

        // 上次初始化失败过的包：先尝试用已落盘的解密 dex 恢复（Init 可能在超时后于后台完成），
        // 恢复不了则直接报错，不再重复调用壳的 Init —— 否则会一直弹「正在初始化本地代理」。
        failedInit[key]?.let { err ->
            tryRecoverLoader(key)?.let { recovered ->
                failedInit.remove(key)
                loaders[key] = recovered
                // 恢复路径跳过了 prepareLoader，这里也要启动本地视频代理
                if (localServerStarted.add(key)) ensureLocalProxy(key, recovered)
                return key
            }
            fail(err)
        }

        try {
            prepareLoader(key, url, md5)
        } catch (e: Exception) {
            failedInit[key] = e.message ?: "spider 包加载失败"
            throw e
        }
        return key
    }

    private suspend fun prepareLoader(key: String, url: String, md5: String) {
        // 1) 下载整个 JAR（保持 zip 原样，不解压）
        val jarFile = File(jarDir, "$key.jar")
        if (!jarFile.exists() || (md5.isNotBlank() && md5Hex(jarFile) != md5)) {
            download(url, jarFile)
            if (md5.isNotBlank() && md5Hex(jarFile) != md5) {
                fail("spider 包 MD5 校验失败（期望 $md5）")
            }
        }
        // Android 14（targetSdk 34）禁止从可写文件加载 dex，必须置为只读
        if (jarFile.canWrite()) jarFile.setReadOnly()
        jars[key] = jarFile.absolutePath

        // 2) 壳 dex 的 classloader
        val stubLoader = DexClassLoader(jarFile.absolutePath, optDir.absolutePath, null, ctx().classLoader)

        // 3) 调 Init.init(context) —— 让壳自己去解 so + 解密 guard。
        //    用独立线程 + 超时等待：壳的本地代理/JSVM 初始化可能挂起（就是一直提示
        //    「正在初始化本地代理」的那个动作），纯阻塞反射调用无法被协程取消，必须由超时兜底。
        //    另外壳的 Init 会回连本地代理（127.0.0.1:8964）确认就绪，代理慢启动时首次
        //    Init 会抛 ConnectException，需隔几秒重试直到代理起来。
        val initStarted = System.currentTimeMillis()
        // 记在调用之前：Init 超时抛异常时这个时刻也要留下，恢复路径靠它算解密 dex 的新鲜度
        initStartedAt[key] = initStarted

        // 必须在 Init 之前把 8964/9978 占上：壳 native 初始化会从 8964 起逐个端口递增探测
        // 本地代理，一直扫到 9978 才结束（实测 1014 个端口 × 23ms ≈ 24 秒，就是冷启动卡的那段）。
        // 8964 有监听时第一步探测即命中，扫描整段被跳过。
        val localProxyUp = LocalProxyServer.start()

        val initClazz = runCatching { stubLoader.loadClass("com.github.catvod.spider.Init") }
            .getOrElse {
                fail("spider 包缺少 Init 类：${it.message}")
            }
        try {
            invokeInitTimed(initClazz)
        } catch (e: Exception) {
            fail("spider 壳初始化失败：${e.message}")
        }
        // 必须等 Init 完成才绑定：壳的 native proxyInvoke 会用 Init.loader() 的返回值去 loadClass，
        // Init 没跑完时该值还是 null，直接在 JNI 层 abort 整个进程（已实测）。
        LocalProxyServer.bind { params -> invokeProxyInvoke(initClazz, params) }

        // 4) 主 loader 一律用「jar + 扫到的解密 dex」自己组，壳 Init.loader() 只收进备用表。
        //    真机证据（旧版把壳 loader 当主 loader，结果整条链路 CNFE、播放全挂）：
        //    新牛二壳 loader() 返回的 DexClassLoader 的 DexPathList 里只有
        //    code_cache/sharedb/config.db，压根不含解密出来的爬虫代码；壳是把解密 dex
        //    挂回 jar 自己那个 loader 的 pathList，所以 jar 组的 loader 才加载得到 Guard 类。
        val shellLoader = tryGetShellLoader(initClazz)
        shellLoader?.let { shellLoaders[key] = it }
        val decrypted = scanDecryptedDex(jarFile.lastModified())
        Log.w(
            TAG,
            "扫描解密 dex ${decrypted.size} 个：${decrypted.joinToString { File(it).name }} " +
                "（壳 loader=${shellLoader?.javaClass?.name ?: "无"}，仅作备用）"
        )
        decrypted.forEach { runCatching { File(it).takeIf { f -> f.canWrite() }?.setReadOnly() } }
        val classpath = (listOf(jarFile.absolutePath) + decrypted).joinToString(File.pathSeparator)
        val loader: ClassLoader =
            if (classpath == jarFile.absolutePath) stubLoader
            else DexClassLoader(classpath, optDir.absolutePath, null, ctx().classLoader)

        loaders[key] = loader

        // 5) 本地视频代理（LocalServer，默认端口 9978）。
        //    playerContent 返回的视频地址形如 http://127.0.0.1:9978/proxy?...，需要监听该端口。
        //    自建的 LocalProxyServer 已在 Init 之前占住 9978，此时反射启动必然端口占用，
        //    只在自建服务没起来时（端口被别的东西占了）才走壳里的反射兜底。
        if (localServerStarted.add(key) && !localProxyUp) {
            startLocalServerWithFallback(key, loader)
        }
    }

    /** 调用壳暴露的 Init.proxyInvoke(Map)：本地代理收到请求后由壳的 native 生成响应 */
    private fun invokeProxyInvoke(initClazz: Class<*>, params: Map<String, String>): Array<Any?>? = runCatching {
        initClazz.getDeclaredMethod("proxyInvoke", java.util.Map::class.java)
            .invoke(null, params) as? Array<Any?>
    }.getOrElse {
        Log.w(TAG, "proxyInvoke 调用失败：${it.javaClass.simpleName}: ${it.message}")
        null
    }

    /** 缓存命中路径没有 initClazz，从已加载的 loader 里取 Init 再绑定 */
    private fun bindProxyInvoke(loader: ClassLoader) {
        val initClazz = runCatching { loader.loadClass("com.github.catvod.spider.Init") }.getOrNull() ?: return
        LocalProxyServer.bind { params -> invokeProxyInvoke(initClazz, params) }
    }

    /** 本地代理分发桥：遍历已加载的爬虫实例，谁认领这个请求（do=xx）谁处理；
     *  全部返回 null 才回落壳 native invoker。对齐 FongMi JarLoader.tryOthers 语义。 */
    fun proxyDispatch(params: Map<String, String>): Array<Any?>? {
        for (proxy in instances.values) {
            val result = proxy.proxy(params) ?: continue
            if (result.isNotEmpty() && result[0] != null) return result
        }
        return null
    }

    /**
     * 启动 spider 内置的本地视频代理（LocalServer）。
     *
     * 云盘线路的 playerContent 会返回 http://127.0.0.1:9978/proxy?... 这样的地址，
     * 这个本地代理负责把请求转发到真实 CDN 并带上 Cookie。若不启动，播放器会
     * 收到 ECONNREFUSED → ERROR_CODE_IO_UNSPECIFIED。
     *
     * 用反射加载并调用，因为 LocalServer 在 spider JAR（加密壳）里，类名可能是
     * com.github.catvod.crawler.LocalServer 或 com.github.catvod.spider.LocalServer。
     * 启动失败不致命（不影响列表/详情爬取），返回 false 让调用方换另一个 loader 再试。
     */
    private fun startLocalServer(loader: ClassLoader): Boolean {
        val classNames = listOf(
            "com.github.catvod.crawler.LocalServer",
            "com.github.catvod.spider.LocalServer"
        )
        for (name in classNames) {
            val clazz = runCatching { loader.loadClass(name) }.getOrNull() ?: continue
            try {
                // 单例：static getInstance()
                val getInstance = runCatching { clazz.getDeclaredMethod("getInstance") }.getOrNull()
                val instance = getInstance?.invoke(null)
                if (instance == null) {
                    Log.w(TAG, "LocalServer($name).getInstance() 返回 null，跳过")
                    continue
                }
                // 尝试 start() 或 start(int timeout, boolean daemon)
                val started = runCatching {
                    clazz.getMethod("start").invoke(instance)
                }.isSuccess || runCatching {
                    clazz.getMethod("start", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                        .invoke(instance, 5000, false)
                }.isSuccess
                if (started) {
                    Log.w(TAG, "LocalServer($name) 已启动（视频代理端口 9978）")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "启动 LocalServer($name) 失败：${e.message}")
            }
        }
        // 带上 loader 身份和它真正看得见的搜索路径：这是判断「壳解密后的类到底在不在这个
        // loader 里」的唯一证据（新牛二壳在 native 内存解密，光看类名猜不出来）。
        Log.w(TAG, "未找到或无法启动 LocalServer（云盘视频代理可能不可用）；loader=${loader.javaClass.name} 搜索路径=${dumpSearchPath(loader)}")
        return false
    }

    /**
     * 让 loader 去加载一个必然不存在的类，把 ClassNotFoundException 里的 DexPathList 打出来。
     * 反射 getURLs() 在这套壳给的 loader 上取不到（实测只会拿到空/异常），别再用；
     * CNFE 的 message 由系统拼好，一定包含 zip 路径和 nativeLibraryDirectories。
     */
    private fun dumpSearchPath(loader: ClassLoader): String = runCatching {
        loader.loadClass("com.chen.doudoutv.SpiderProbe")
        "(探针类意外加载成功，说明 loader 里有 App 自身的 dex)"
    }.getOrElse { it.message ?: "(异常无 message)" }

    /** 先拿主 loader 起 LocalServer，起不来再用壳 loader 兜一次（两类壳各把 LocalServer 放一边） */
    private fun startLocalServerWithFallback(key: String, primary: ClassLoader) {
        if (startLocalServer(primary)) return
        shellLoaders[key]?.let { startLocalServer(it) }
    }

    /** 确保 9978 有服务：优先自建（同时挂上壳的 proxyInvoke），失败才退回反射启动壳里的 LocalServer */
    private fun ensureLocalProxy(key: String, loader: ClassLoader) {
        if (LocalProxyServer.start()) {
            bindProxyInvoke(loader)
            return
        }
        startLocalServerWithFallback(key, loader)
    }

    /**
     * 带超时的 Init.init 调用（最多 2 次 + Go 代理端口轮询）。
     *
     * 关键发现（来自真机日志）：
     *  1. 壳的 Init.init 内部会下载配置、解压 so、在后台线程启动 Go 代理；
     *  2. Go 代理监听**随机端口**（每次启动不同，如 24038 / 42089 / 32600），不是固定 8964；
     *  3. Init.init 内部会反复重试连接 Go 代理，一旦代理就绪就会继续解密 dex 并正常返回；
     *     若超时/重试耗尽则会抛异常。
     *
     * 策略：
     *  - 第 1 次 callInit 给足 60s，让 Init.init 内部有时间启动 Go 代理并完成 dex 解密。
     *    只要不抛异常就视为成功（之前牛二等站点就是这样加载成功的）。
     *  - 第 1 次失败时，扫描本机监听端口找到响应 {"state":"ok"} 的 Go 代理，
     *    等它就绪后调第 2 次 Init.init。
     */
    private suspend fun invokeInitTimed(initClazz: Class<*>): Any? {
        // 第 1 次：给足时间让壳内部完成 Go 代理启动 + dex 解密
        try {
            return callInit(initClazz, INIT_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "壳初始化（第 1 次）失败：${e.message}")
        }

        // 第 1 次失败：等 Go 代理就绪后重试
        val proxyPort = waitGoProxyReady(PROXY_WAIT_MS)
        if (proxyPort == null) {
            throw IllegalStateException(
                "壳初始化失败：Go 代理未启动（监听端口中未找到响应 {\"state\":\"ok\"} 的服务）"
            )
        }
        Log.w(TAG, "Go 代理就绪：127.0.0.1:$proxyPort，重试 Init.init")
        return callInit(initClazz, INIT_TIMEOUT_MS)
    }

    private fun callInit(initClazz: Class<*>, timeoutMs: Long): Any? {
        val future = initExecutor.submit<Any?> { invokeInit(initClazz) }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw IllegalStateException(
                "壳初始化超时（${timeoutMs / 1000}s 未完成，通常是本地代理/JSVM 初始化挂起）"
            )
        } catch (e: java.util.concurrent.ExecutionException) {
            throw IllegalStateException("壳初始化失败：${e.cause?.message ?: e.message}")
        } catch (e: InterruptedException) {
            throw IllegalStateException("壳初始化被中断")
        }
    }

    /** Guard 类 loadClass/newInstance 的带超时执行：超时或异常都返回 null（异常原因由调用方通过
     *  lastError 描述），保证壳 Guard 类 static{}/构造器无限阻塞时流程不会挂死 */
    private fun <T> timedClass(block: () -> T): T? {
        val future = classExec.submit<T?> { block() }
        return try {
            future.get(CLASS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "Guard 类加载/实例化超时（${CLASS_TIMEOUT_MS / 1000}s 未返回，已放弃等待）")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Guard 类加载/实例化异常：${e.message}")
            null
        }
    }

    /**
     * 轮询等待 Go 代理就绪。
     * Go 代理监听随机端口，通过扫描 /proc/net/tcp(6) 找监听端口，
     * 再逐个 HTTP GET 探测，响应体含 {"state":"ok"} 即为目标代理。
     */
    private suspend fun waitGoProxyReady(timeoutMs: Long): Int? {
        // 新的一轮等待：把兜底扫描的游标复位（上一轮扫过的端口，这一轮才可能刚被代理监听上）
        fallbackScanIndex = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            findGoProxyPort()?.let { return it }
            delay(PROXY_WAIT_INTERVAL_MS)
        }
        return findGoProxyPort()
    }

    /** 扫描本机所有监听端口，找 Go 代理（GET /health 返回 {"state":"ok"}） */
    private fun findGoProxyPort(): Int? {
        val ports = scanListeningPorts()
        // 只印前 20 个：兜底扫描可能一下给出上百个端口，整行日志会被 ROM 截断，
        // 真要排查时前 20 个足够判断「有没有扫到」
        Log.w(TAG, "扫描到监听端口 ${ports.size} 个：${ports.take(20)}")
        for (port in ports) {
            val ok = isGoProxy(port)
            if (ok) {
                Log.w(TAG, "找到 Go 代理端口：$port")
                return port
            }
        }
        return null
    }

    /**
     * 获取本机所有 LISTEN 状态的端口。
     * 优先读 /proc/net/tcp(6)；若读不到（SELinux 限制等），则用 Socket 探测常用端口范围。
     */
    private fun scanListeningPorts(): List<Int> {
        val out = LinkedHashSet<Int>()
        // 1) 优先从 proc 读取（快、全量）
        for (path in arrayOf("/proc/net/tcp", "/proc/net/tcp6")) {
            runCatching {
                File(path).bufferedReader().useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val fields = line.trim().split("\\s+".toRegex())
                        if (fields.size > 3 && fields[3] == "0A") { // 0A = LISTEN
                            val local = fields[1]
                            val portHex = local.substringAfterLast(':')
                            portHex.toIntOrNull(16)?.takeIf { it in 1025..65535 }?.let { out += it }
                        }
                    }
                }
            }
        }
        if (out.isNotEmpty()) return out.toList()

        // 2) Fallback：Socket 探测，见 [scanPortsByConnect] 的预算说明
        out += scanPortsByConnect()
        return out.toList()
    }

    /** 兜底扫描扫到哪儿了；-1 表示本轮已扫完，后续轮询不再重复扫 */
    /** 兜底扫描扫到第几个端口（按 [FALLBACK_PORT_BANDS] 展平后的下标）；扫完不再重复 */
    @Volatile
    private var fallbackScanIndex = 0

    /** 展平下标 → 端口；越界返回 null 表示整个带区已经扫过 */
    private fun fallbackPortAt(index: Int): Int? {
        var n = index
        for (band in FALLBACK_PORT_BANDS) {
            val size = band.last - band.first + 1
            if (n < size) return band.first + n
            n -= size
        }
        return null
    }

    /**
     * proc 读不到时的兜底端口探测。
     *
     * 原来这里一次 `for (port in 20000..50000)` 三万多次 connect，外面还套着 2 秒一次的轮询：
     * ROM 拒绝/拖慢本机 connect 时每个端口都要等满超时，最坏能把 Init 的整段等待预算
     * 全耗在端口探测上，代理明明已经就绪也等不到。
     * 现在按带区顺序推进（先 8964 那段实测常用的，再大区），每轮最多花
     * [FALLBACK_SCAN_BUDGET_MS]，扫到哪儿记到哪儿，扫完就不再重复。
     */
    private fun scanPortsByConnect(): List<Int> {
        val started = SystemClock.elapsedRealtime()
        val out = LinkedHashSet<Int>()
        var index = fallbackScanIndex
        while (true) {
            val port = fallbackPortAt(index) ?: break
            if (isPortOpen(port)) out += port
            index++
            if (SystemClock.elapsedRealtime() - started > FALLBACK_SCAN_BUDGET_MS) break
        }
        fallbackScanIndex = index
        Log.w(
            TAG,
            "端口兜底扫描：已推进到第 $index 个，命中 ${out.size} 个" +
                "（本轮用时 ${SystemClock.elapsedRealtime() - started}ms）"
        )
        return out.toList()
    }

    private fun isPortOpen(port: Int): Boolean = runCatching {
        java.net.Socket().use { s -> s.connect(java.net.InetSocketAddress("127.0.0.1", port), 50) }
    }.isSuccess

    /**
     * 探测某端口是否是 Go 代理（GET /health 返回 {"state":"ok"}）。
     * 用原生 Socket 发 HTTP，避免 HttpURLConnection 在某些 ROM 上的策略问题。
     */
    private fun isGoProxy(port: Int): Boolean = runCatching {
        java.net.Socket().use { s ->
            s.connect(java.net.InetSocketAddress("127.0.0.1", port), 800)
            s.soTimeout = 800
            s.getOutputStream().apply {
                write("GET /health HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            val body = s.getInputStream().bufferedReader().readText()
            body.contains("\"state\":\"ok\"")
        }
    }.getOrElse { false }

    /** 不触发壳 Init，直接用已落盘的解密 dex 重建 classloader（兼容 Init 超时后于后台完成的情况） */
    private fun tryRecoverLoader(key: String): DexClassLoader? {
        val jarPath = jars[key] ?: return null
        // 同 loadGuardClass：按这个包跑 Init 的时刻算新鲜度，没有记录才退回 10 分钟窗口
        val decrypted = scanDecryptedDex(jarSince(key))
        if (decrypted.isEmpty()) return null
        decrypted.forEach { runCatching { File(it).takeIf { f -> f.canWrite() }?.setReadOnly() } }
        val classpath = (listOf(jarPath) + decrypted).joinToString(File.pathSeparator)
        return runCatching { DexClassLoader(classpath, optDir.absolutePath, null, ctx().classLoader) }.getOrNull()
    }

    private fun invokeInit(initClazz: Class<*>): Any? {
        // 饭太硬等加密壳要求 init(Context) 的实参必须是 Application（dex 内 check-cast 到 Application），
        // 传普通 Context 会抛 ClassCastException → native 解密不执行 → Init.loader() 为 null。
        val appCtx: Context = ctx().applicationContext

        // 常见签名：static void init(Context) / static void init(Context, String)
        runCatching {
            initClazz.getDeclaredMethod("init", Context::class.java).invoke(null, appCtx)
        }.onSuccess { return it }
        return runCatching {
            initClazz.getDeclaredMethod("init", Context::class.java, String::class.java).invoke(null, appCtx, "")
        }.getOrElse { throw it }
    }

    /**
     * 从壳 Init 暴露的静态方法拿 ClassLoader，存进 [shellLoaders] 作补充尝试。
     *
     * 注意：这**不是**主 loader。真机日志证明新牛二壳 loader() 返回的 DexClassLoader
     * 里只有 code_cache/sharedb/config.db，不含解密出来的爬虫代码，拿它去 loadClass
     * Guard 会 ClassNotFoundException。主路径仍然是「jar + 扫描到的解密 dex」自己组的
     * loader（壳把解密 dex 挂回的是 jar loader 的 pathList）。
     */
    private fun tryGetShellLoader(initClazz: Class<*>): ClassLoader? {
        for (name in listOf("loader", "getLoader")) {
            val method = runCatching { initClazz.getDeclaredMethod(name).apply { isAccessible = true } }.getOrNull()
                ?: continue
            val cl = runCatching { method.invoke(null) as? ClassLoader }.getOrNull() ?: continue
            Log.w(TAG, "壳 Init.$name() 返回 ClassLoader：${cl.javaClass.name}（收作备用，不作主 loader）")
            return cl
        }
        return null
    }

    /**
     * 递归扫描壳解密的产物（以 dex 魔数开头的文件）。
     *
     * 扫的范围比原来宽：原来只看 cacheDir、且时间窗卡在「Init 开始之后」，
     * 但这套壳的解密产物不一定落在 cacheDir，也不一定在 Init 之后才落盘
     * （有的壳在 Init 之前就解好了，进程重启后文件还留在盘上）。
     * 窗口卡太窄的结果就是「壳初始化完成但一个 dex 都没扫到」→
     * 找不到爬虫类 → 站点内容永远是空的，只能退出重开碰运气。
     *
     * @param after 起始时间，一般传 spider jar 的落盘时间（比 Init 时间更早，窗口更宽）
     */
    private fun scanDecryptedDex(after: Long): List<String> {
        val out = ArrayList<String>()
        // 诊断：扫所有落盘文件（不过滤大小/时间/魔数），看壳 Init 后到底产了什么
        val allFiles = ArrayList<String>()
        fun walk(dir: File, depth: Int) {
            if (depth > 5) return
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) walk(f, depth + 1)
                else {
                    allFiles += "${f.absolutePath} size=${f.length()} mtime=${f.lastModified()} name=${f.name}"
                    if (f.length() > 4096 && f.lastModified() >= after && hasDexMagic(f) &&
                        !f.name.endsWith(".odex") && !f.name.endsWith(".vdex")
                    ) {
                        out += f.absolutePath
                    }
                }
            }
        }
        val ctx = ctx()
        listOf(ctx.cacheDir, ctx.filesDir, ctx.codeCacheDir, ctx.noBackupFilesDir)
            .forEach { walk(it, 0) }
        if (out.isEmpty()) {
            Log.w(TAG, "解密 dex 扫描为空，所有落盘文件如下：")
            allFiles.forEach { Log.w(TAG, "  $it") }
        }
        return out
    }

    /**
     * 解密 dex 的扫描起点：spider jar 的落盘时间。
     *
     * 比「Init 开始时间」更早 —— 壳可能在 Init 之前就解好了，也可能上次进程留下的还在盘上，
     * 用 Init 时间当起点会把这些产物全漏掉（表现：扫到 0 个 dex → 找不到爬虫类）。
     */
    private fun jarSince(key: String): Long =
        jars[key]?.let { runCatching { File(it).lastModified() }.getOrDefault(0L) }
            ?: (System.currentTimeMillis() - 10 * 60_000)

    private fun hasDexMagic(f: File): Boolean = runCatching {
        f.inputStream().use { ins ->
            val buf = ByteArray(4)
            ins.read(buf) == 4 && buf.contentEquals(DEX_MAGIC)
        }
    }.getOrDefault(false)

    fun getSpider(loaderKey: String, api: String, ext: Any?): SpiderProxy? {
        val cacheKey = "$loaderKey:$api"
        instances[cacheKey]?.let { return it }

        // Python(.py)/QuickJS(.js)/Node(.md5、node:) 爬虫需要独立运行时，本版本只实现了 Java csp_ 一条路。
        // 必须在这里快速失败：否则 resolveClassName 会把 .py/.js 的地址盲拼成类名，
        // 触发扫盘 + 重建 classloader + 逐站 30s 超时，详情表现为长时间转圈后空白，
        // 首页还会误报「加密壳未能解密」。给出准确原因，交由上层跳过该站。
        if (isUnsupportedApi(api)) {
            lastError = "该线路使用 Python/JS 爬虫（$api），当前版本仅支持 Java 爬虫，暂不支持"
            Log.w(TAG, lastError)
            return null
        }

        val className = resolveClassName(api)
        val clazz = loadGuardClass(loaderKey, className, api) ?: run {
            lastError = "找不到爬虫类 $className（spider 包解密失败或站点名不匹配）"
            Log.e(TAG, lastError)
            return null
        }

        var newInstanceError: Throwable? = null
        val instance = timedClass<Any?> {
            runCatching {
                clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            }.getOrElse {
                newInstanceError = it
                null
            }
        }
        if (instance == null) {
            lastError = "实例化 $className 失败：${newInstanceError?.message ?: "超时（${CLASS_TIMEOUT_MS / 1000}s 未返回）"}"
            Log.e(TAG, lastError, newInstanceError)
            return null
        }

        val proxy = SpiderProxy(clazz, instance)
        if (!proxy.init(ctx(), extStr(ext))) {
            // 壳 spider 的 init 可能依赖本地代理/JSVM，失败或超时不致命，
            // 但绝不能无限阻塞主流程 —— 已由 SpiderProxy 内 30s 超时兜底
            Log.w(TAG, "$className.init 失败或超时（不致命，继续尝试）")
        }
        instances[cacheKey] = proxy
        return proxy
    }

    /**
     * 加载 Guard 类。首次失败时重新扫描 cacheDir 里的解密 dex 并重建 classloader，
     * 兼容「Init 懒解密 / 首个 class 请求时才落盘」的实现。
     */
    private fun loadGuardClass(loaderKey: String, className: String, api: String): Class<*>? {
        loaders[loaderKey]?.let { l ->
            timedClass { l.loadClass(className) }?.let { return it }
            timedClass { l.loadClass(api) }?.let { return it }
        }

        // 主 loader 里没有，再拿壳 Init.loader() 返回的那个试一次：个别壳确实只把解密类放进它。
        // 这里只作补充尝试 —— 真机实测这套壳的 loader 里只有 config.db，不能当主 loader。
        shellLoaders[loaderKey]?.let { l ->
            timedClass { l.loadClass(className) }?.let { return it }
            timedClass { l.loadClass(api) }?.let { return it }
        }

        val jarPath = jars[loaderKey] ?: return null
        // 新鲜度按「这个包什么时候跑的 Init」算，不能写死 60 秒：
        // 启动预热跑完 Init 后可能几分钟才播放，固定窗口那时必然扫空。
        val decrypted = scanDecryptedDex(jarSince(loaderKey))
        if (decrypted.isEmpty()) return null
        decrypted.forEach { runCatching { File(it).takeIf { f -> f.canWrite() }?.setReadOnly() } }

        val classpath = (listOf(jarPath) + decrypted).joinToString(File.pathSeparator)
        val rebuilt = runCatching {
            DexClassLoader(classpath, optDir.absolutePath, null, ctx().classLoader)
        }.getOrNull() ?: return null

        loaders[loaderKey] = rebuilt
        return timedClass { rebuilt.loadClass(className) } ?: timedClass { rebuilt.loadClass(api) }
    }

    private fun extStr(ext: Any?): String = when (ext) {
        null -> ""
        is String -> ext
        else -> ext.toString()
    }

    private fun resolveClassName(api: String): String {
        var name = api
        if (name.startsWith("csp_")) name = name.removePrefix("csp_")
        if (name.startsWith("com.github.catvod.spider.")) return api
        return "com.github.catvod.spider.$name"
    }

    /**
     * 非 Java 爬虫入口判定：.py/.js/.md5/node: 前缀。去掉 query/fragment 再判后缀；
     * Java 入口只会是 csp_Xxx 或全限定类名，不会误命中。
     *
     * 故障档案（直播详情卡死，方案①已止血）：py 站曾因盲拼类名 + detail 全库串行兜底
     * （每站吃满 30s 超时）而卡死，现 getSpider 快速失败、detail 只查本站。
     *
     * py 真支持路径（未做，重开时照此不必再调研）：FongMi 三语言按 api 路由，py 契约
     * 方法名与 Java 完全一致、返回 dict 由宿主序列化成同一套 Result JSON；接入点就是
     * 写一个同签名的 py 桥接类路由 .py 即可，SpiderProxy/UI 不用动。引擎 Chaquopy
     * （12.0.1 起免费开源，需 abiFilters 双 arm，+15–25MB）；长尾是自研 base.spider
     * 兼容层 + pip 依赖 wheel。红线：只参考 FongMi 行为，GPL 源码禁止复制、净室自写。
     */
    private fun isUnsupportedApi(api: String): Boolean {
        val a = api.trim().substringBefore('?').substringBefore('#').lowercase()
        return a.endsWith(".py") || a.endsWith(".js") || a.endsWith(".md5") || a.startsWith("node:")
    }

    private fun fail(msg: String): Nothing {
        lastError = msg
        Log.e(TAG, msg)
        throw IllegalStateException(msg)
    }

    private fun download(url: String, target: File) {
        // 旧文件可能是只读的（上一轮已置只读），先恢复可写再覆盖
        if (target.exists()) target.setWritable(true)
        val punyUrl = TvBoxClient.toPunycode(url)
        val request = Request.Builder().url(punyUrl).get().build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) fail("下载 spider 包失败 HTTP ${resp.code}")
            val body = resp.body ?: fail("下载 spider 包返回空内容")
            FileOutputStream(target).use { fos -> body.byteStream().copyTo(fos) }
        }
    }

    private fun md5Hex(file: File): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(s: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Spider 实例的反射代理。
 * CatVod Spider 基类的标准方法：
 *   void init(Context, String)
 *   String homeContent(boolean)
 *   String categoryContent(String, String, boolean, HashMap<String,String>)
 *   String detailContent(List<String>)
 *   String playerContent(String, String, List<String>)
 *   String searchContent(String, boolean)
 */
class SpiderProxy(private val clazz: Class<*>, private val instance: Any) {

    companion object {
        private const val TAG = "SpiderProxy"
        private const val CALL_TIMEOUT_MS = 30_000L
    }

    /** 爬虫方法统一走独立线程 + 超时：壳内方法常自带长阻塞（网络/JSVM），协程取消对纯阻塞无效，由超时兜底 */
    private val exec: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "spider-call").apply { isDaemon = true }
    }

    private fun <T> timedCall(block: () -> T): T? {
        val future = exec.submit<T?> { block() }
        return try {
            future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "爬虫方法调用超时（${CALL_TIMEOUT_MS / 1000}s 未返回，已放弃等待）")
            null
        } catch (e: Exception) {
            // 反射调用抛 InvocationTargetException，真实原因在 cause 里，必须拆开透出
            val cause = if (e is java.lang.reflect.InvocationTargetException) e.cause else e
            val msg = cause?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: e.message
            Log.w(TAG, "爬虫方法调用异常：$msg")
            if (cause != null) Log.w(TAG, "爬虫方法调用异常堆栈", cause)
            null
        }
    }

    /** 壳 spider 实例的 init(Context, String)。走 30s 超时，避免壳实现阻塞时挂死整个流程 */
    fun init(context: Context, ext: String): Boolean {
        return runCatching {
            timedCall<Boolean> {
                clazz.getMethod("init", Context::class.java, String::class.java)
                    .invoke(instance, context, ext)
                true
            }
        }.getOrDefault(false) == true
    }

    fun homeContent(filter: Boolean = true): String? = timedCall {
        clazz.getMethod("homeContent", Boolean::class.javaPrimitiveType).invoke(instance, filter) as String
    }

    fun categoryContent(
        tid: String,
        pg: String,
        filter: Boolean = true,
        extend: Map<String, String>? = null
    ): String? = timedCall {
        // 反射签名要的是 HashMap 本体：调用方传的多是 LinkedHashMap/Map 视图，
        // 直接 as? HashMap 会静默失败成空 map（筛选值永远传不进爬虫），必须显式拷贝一份
        val map = if (extend.isNullOrEmpty()) HashMap<String, String>() else HashMap(extend)
        clazz.getMethod(
            "categoryContent",
            String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType, HashMap::class.java
        ).invoke(instance, tid, pg, filter, map) as String
    }

    fun detailContent(ids: List<String>): String? = timedCall {
        clazz.getMethod("detailContent", List::class.java).invoke(instance, ids) as String
    }

    fun playerContent(flag: String, id: String, vipFlags: List<String>? = null): String? = timedCall {
        clazz.getMethod("playerContent", String::class.java, String::class.java, List::class.java)
            .invoke(instance, flag, id, vipFlags ?: emptyList<String>()) as String
    }

    fun searchContent(key: String, quick: Boolean = true): String? = timedCall {
        clazz.getMethod("searchContent", String::class.java, Boolean::class.javaPrimitiveType)
            .invoke(instance, key, quick) as String
    }

    /** 爬虫实例的 proxy(Map)：plain jar 爬虫（不经过壳 native）的本地代理请求只有实例方法能处理。
     *  返回 Object[]（同壳 proxyInvoke 契约：body/headers/code）；无人认领或异常返回 null 由上层回落。 */
    fun proxy(params: Map<String, String>): Array<Any?>? = timedCall {
        clazz.getMethod("proxy", java.util.Map::class.java).invoke(instance, params) as? Array<Any?>
    }
}
