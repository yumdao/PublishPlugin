package com.yumdao.daydayup

import org.gradle.api.DefaultTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

open class PublisherTask : DefaultTask() {

    //是否完成执行任务
    private var executeFinishFlag: AtomicBoolean = AtomicBoolean(false)

    //检验状态是否通过
    private var checkStatus = false

    private lateinit var publisher: Publisher

    init {
        group = "upload"

        project.run {
            publisher = extensions.getByName(Publisher.NAME) as Publisher

            //动态为该模块引入上传插件
            apply(hashMapOf<String, String>(Pair("plugin", "maven-publish")))
            val publishing = project.extensions.getByType(PublishingExtension::class.java)

            afterEvaluate {
                components.forEach {
                    if (it.name == "release") {
                        publishing.publications { publications ->
                            //注册上传task
                            publications.create("release",
                                MavenPublication::class.java) { publication ->
                                publication.groupId = publisher.libGroup
                                publication.artifactId = publisher.libArtifact
                                publication.version = publisher.libVersion

                                publication.from(it)
                            }
                        }

                        publishing.repositories { artifactRepositories ->
                            artifactRepositories.maven { mavenArtifactRepository ->
                                mavenArtifactRepository.url =
                                    if (publisher.libVersion.endsWith("SNAPSHOT")) {
                                        URI(publisher.repoSnapshot)
                                    } else {
                                        URI(publisher.repoRelease)
                                    }
                                mavenArtifactRepository.credentials { credentials ->
                                    credentials.username = publisher.repoAccount
                                    credentials.password = publisher.repoPassword
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @TaskAction
    fun doTask() {

        executeTask()

        //开启线程守护，防止子线程任务还没执行完毕，task就已经结束了
        while (!executeFinishFlag.get()) {
            Thread.sleep(500)
        }
    }

    private fun executeTask() {
        //1、对publisher配置的信息进行基础校验
        //2、把publisher上传到服务器端，做版本重复性校验
        checkStatus = requestCheckVersion()

        //如果前两步都校验通过了，checkStatus设置为true

        if (checkStatus) {
            val out = ByteArrayOutputStream()
            //通过命令行的方式进行调用上传maven的task
            project.exec { exec ->
                exec.standardOutput = out
                exec.isIgnoreExitValue = true
                exec.commandLine(
                    "${project.rootDir}/gradlew",
                    "publishReleasePublicationToMavenRepository"
                )
            }
            val result = out.toString()
            if (result.contains("UP-TO-DATE")) {
                //上传maven仓库成功，上报到服务器
                val isSuccess = requestUploadVersion()
                if (isSuccess) {
                    //提示成功信息
                } else {
                    //提示错误信息
                }
                executeFinish()
            } else {
                throw Exception("上传Maven仓库失败，请检查配置！")
            }
        }
    }

    private fun requestCheckVersion(): Boolean {
        //TODO 上报服务器进行版本检查,这里直接模拟返回成功
        return true
    }

    private fun requestUploadVersion(): Boolean {
        //TODO 上报服务器进行版本更新操作,这里直接模拟返回成功
        return true
    }

    /**
     * 任务执行完毕
     */
    private fun executeFinish() {
        executeFinishFlag.set(true)
    }
}