package com.chen.doudoutv

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.ui.LibraryViewModel
import com.chen.doudoutv.ui.phone.PhoneApp
import com.chen.doudoutv.ui.settings.forceTvMode
import com.chen.doudoutv.ui.theme.AppColors
import com.chen.doudoutv.ui.theme.DouDouTvTheme
import com.chen.doudoutv.ui.theme.isTelevision
import com.chen.doudoutv.ui.tv.TvApp
import com.chen.doudoutv.ui.tv.TvTextFieldBack

/**
 * 唯一的 Activity 入口，只做一件事：判断这是不是电视，然后交给对应的一套实现。
 *
 * 手机端入口是 ui/phone/PhoneApp.kt，电视端入口是 ui/tv/TvApp.kt，
 * 两边各自拥有独立的导航结构、页面、组件与播放器，不通过 isTv 参数在同屏里分叉。
 * 这样改手机端界面不会牵连电视端，反之亦然。
 */
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * 爬虫壳触发原地重建的节流窗口（静态）：
         * recreate() 会产生新的 Activity 实例，实例字段会被重置，所以节流状态必须放静态。
         * 10 秒内超过 3 次说明重建后又被爬虫反复关，此时放行 finish 避免死循环。
         */
        private var spiderRestartWindowStart = 0L
        private var spiderRestartCount = 0

        /**
         * 当前是否停留在详情页（手机/TV 共用）。
         * 爬虫壳在拉取详情（网盘线路）时会顺手 finish 宿主 Activity，
         * 这个期间把 finish() 吞掉，避免「点详情直接闪退」。
         */
        @Volatile
        var detailVisible = false
    }

    private var textFieldBackConsumed = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && TvTextFieldBack.consume()) {
                textFieldBackConsumed = true
                return true
            }
            if (event.action == KeyEvent.ACTION_UP && textFieldBackConsumed) {
                textFieldBackConsumed = false
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 诊断：savedInstanceState 非空 = 本 Activity 是重建出来的（爬虫壳 finish、系统配置变更都算）。
        Log.e("DouDouTvNav", "MainActivity.onCreate 重建恢复=${savedInstanceState != null} pid=${Process.myPid()}")
        // 颜色模式：开机先落 Prefs 里存的那档 + 当前系统深浅，避免「跟随系统」档首帧闪暗色；
        // 之后系统切换深浅色由 DouDouTvTheme 里的 SideEffect 随配置同步
        AppColors.themeMode = Prefs.themeMode
        AppColors.systemDark =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        setContent {
            // 判定只发生在最外层一次：系统 uiMode 为主，设置里的强制开关为辅，
            // 并对部分国产盒子 uiMode 上报不准的情况做了无触摸屏兜底。
            // 字号表也依赖它：电视端要换整套放大过的 typography，所以判定必须在主题外层。
            val isTv = remember { isTelevision() || forceTvMode() }
            DouDouTvTheme(isTv = isTv) {
                // 状态栏 / 导航栏外观不跟应用颜色模式走：系统栏是手机自己的样式，
                // 应用内切浅色/暗色不该改它（2026-11-24 反馈：应用内切浅色把状态栏图标改黑）。
                // 进程级唯一实例：本 Activity 会被系统反复重建（进出播放器各一次），
                // 用 viewModel() 挂在 Activity 上时每重建换一个，线路绑定与分页缓存全丢，详见 LibraryViewModel.shared
                val vm = remember { LibraryViewModel.shared(application) }

                if (isTv) {
                    TvApp(vm = vm)
                } else {
                    PhoneApp(vm = vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // darkUi 是「当前窗口是否恒暗」的全局闸：只有播放器恒暗，浏览界面（手机/TV/强制 TV）
        // 一律跟随颜色模式（TV 端设置里只有浅色/暗色两档）。
        // 放 onResume 而不是组合里：从播放器（恒暗）回来时本页不一定重建，只有这里必然执行，
        // 写快照状态即触发全局重组换肤（状态栏外观随组合里的 SideEffect 一起更新）。
        AppColors.darkUi = false
    }

    /**
     * 诊断日志：偶发「点搜索结果后直接回到桌面」时，用来确认是谁主动结束了本 Activity。
     * 正常业务里没有任何地方调用 finish()，如果真的被调用，这里的堆栈能直接指出调用方。
     *
     * 现已确认调用方是内嵌的 catvod 爬虫壳（云盘/网盘配置流程）：
     * 它打开配置项后会直接 finish 宿主 Activity，靠「宿主重启」让新的配置/二维码界面生效，
     * 表现就是点「网盘配置 / 扫码登录」后回到桌面、进程却还活着，重开才看到二维码。
     *
     * 这里把它拦下来，改用 recreate() 原地重建 MainActivity：
     *   · 任务栈不变，不会闪回桌面；
     *   · 对爬虫壳来说等价于「宿主重启了一次」，它的二维码/配置界面会在重建后弹出。
     * 注意不能用 startActivity 重启自己：本 Activity 是 singleTask，
     * 那样只会给旧实例发 onNewIntent，任务栈清空后照样闪桌面。
     */
    override fun finish() {
        val stack = Log.getStackTraceString(Throwable())
        Log.e("DouDouTvNav", "MainActivity.finish() 被调用\n$stack")

        // 详情页/播放页期间一律忽略 finish()：
        // 内嵌爬虫壳在拉取详情（尤其是网盘线路）时会顺手结束宿主 Activity，
        // 之前用 recreate() 兜底，结果一进详情就被打回首页，表现就是「点详情直接闪退」。
        // 这里直接吞掉，页面保持不动；进程也不会退出。
        if (detailVisible) {
            Log.e("DouDouTvNav", "详情页期间拦截 finish()，保持当前页面")
            return
        }

        if (stack.contains("com.github.catvod.spider")) {
            val now = SystemClock.elapsedRealtime()
            if (now - spiderRestartWindowStart > 10_000L) {
                spiderRestartWindowStart = now
                spiderRestartCount = 0
            }
            spiderRestartCount++
            if (spiderRestartCount <= 3) {
                Log.e("DouDouTvNav", "拦截爬虫壳的 finish()，recreate() 原地重建 MainActivity")
                recreate()
                return
            }
            Log.e("DouDouTvNav", "爬虫壳连续触发 finish() 超过 3 次，放行以免无限重建")
        }
        super.finish()
    }

    /**
     * 诊断：区分两种「本 Activity 没了」的来路。
     * isFinishing=true 是自己 finish 掉的（会先经过上面的 finish()）；
     * isFinishing=false 是被系统按配置变更重建的 —— 那种情况不会留下任何 finish 堆栈，
     * 只看 finish 日志会误判成「没人销毁过」。
     */
    override fun onDestroy() {
        Log.e("DouDouTvNav", "MainActivity.onDestroy isFinishing=$isFinishing pid=${Process.myPid()}")
        super.onDestroy()
    }
}
