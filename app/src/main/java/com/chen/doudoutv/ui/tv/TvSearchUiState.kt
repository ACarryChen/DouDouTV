package com.chen.doudoutv.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chen.doudoutv.core.Prefs
import com.chen.doudoutv.data.model.MediaEntity

/**
 * 电视端搜索页状态：由导航层（TvApp）持有，
 * 进详情再返回时搜索词与结果保留，真正退出搜索页才清空。
 */
class TvSearchUiState {
    var keyword by mutableStateOf("")

    /** 输入过程中的联想片名：只展示，不触发真正的搜索 */
    var suggestions by mutableStateOf<List<String>>(emptyList())

    /** 用户确认要搜的关键字（选中联想 / 点搜索 / 回车才会赋值） */
    var submittedKeyword by mutableStateOf("")

    var results by mutableStateOf<List<MediaEntity>>(emptyList())
    var loading by mutableStateOf(false)
    var searched by mutableStateOf(false)

    /** 远程站点搜索进行中：本地库只有首页入库的少量条目，绝大多数片名要靠各站点远程命中 */
    var remoteSearching by mutableStateOf(false)

    /** 已完成搜索的关键字，从详情返回时据此跳过重复搜索 */
    var searchedKeyword by mutableStateOf("")

    /** 上次搜索执行时的屏蔽版本号：屏蔽/恢复后同词也要重查，过滤或恢复结果 */
    var searchedHideEpoch by mutableStateOf(0)

    /** 热门榜缓存版本，刷新后自增以触发联想重查 */
    var hotEpoch by mutableStateOf(0)

    /**
     * 搜索历史的读取版本：真正提交一次搜索（或清空历史）后自增。
     * 历史本身存在 [Prefs] 里，和手机端共用同一份记录，这里只负责让页面重读。
     */
    var historyEpoch by mutableStateOf(0)
        private set

    fun clear() {
        keyword = ""
        suggestions = emptyList()
        submittedKeyword = ""
        results = emptyList()
        loading = false
        searched = false
        searchedKeyword = ""
        remoteSearching = false
    }

    /** 清空搜索历史，并让读它的页面重查一遍 */
    fun clearHistory() {
        Prefs.clearSearchHistory()
        historyEpoch++
    }

    /**
     * 真正发起一次搜索。
     * @param title 选中联想片名时传入，会回填到输入框
     */
    fun submit(title: String? = null) {
        val kw = (title ?: keyword).trim()
        if (kw.isEmpty()) return
        if (title != null) keyword = title
        suggestions = emptyList()
        submittedKeyword = kw
        // 落一份历史：TV 端搜索都从这一处出去，不会漏记也不会重记
        Prefs.addSearchHistory(kw)
        historyEpoch++
    }
}
