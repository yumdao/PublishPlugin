package com.yumdao.daydayup

open class Publisher {

    companion object {
        const val NAME = "publisher"
    }

    //配置仓库信息
    var repoRelease: String = ""
    var repoSnapshot: String = ""

    //配置账户名和密码
    var repoAccount: String = ""
    var repoPassword: String = ""

    //配置library版本信息
    var libGroup: String = ""
    var libArtifact: String = ""
    var libVersion: String = ""

    //当前版本的更新描述
    var libUpdateDesc: ArrayList<String> = arrayListOf()

    //版本更新描述
    var pomDesc: String = ""
    var pomName: String = ""
    var pomUrl: String = ""

    //发布人
    var publisher: String = ""
}