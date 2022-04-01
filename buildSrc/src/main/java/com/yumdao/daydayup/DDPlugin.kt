package com.yumdao.daydayup

import org.gradle.api.Plugin
import org.gradle.api.Project

class DDPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        //注册自定义参数
        project.extensions.create(Publisher.NAME, Publisher::class.java)

        val currProjectName = project.displayName

        //在afterProject生命周期之后，才可以从build.gradle文件中获取到配置的参数
        project.gradle.afterProject { currProject ->

            //如果是当前模块，才进行task的注册，避免冗余注册
            if (currProjectName == currProject.displayName) {
                //注册上传task，这里不要dependsOn
                project.tasks.create("publishToMaven", PublisherTask::class.java)
            }
        }
    }
}