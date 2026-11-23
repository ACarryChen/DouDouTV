package com.fongmi.chaquo;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

// chaquo 运行时垫片：宿主未引入 Chaquopy（项目决策不做 py 引擎），
// PyLoader 保持 FongMi 原实现，这里提供编译级 shim——spider() 恒返 SpiderNull，
// py 爬虫源在运行期与本机无 py 引擎时行为一致（加载即空）。
public class Loader {

    public Spider spider(String api) {
        return new SpiderNull();
    }
}
