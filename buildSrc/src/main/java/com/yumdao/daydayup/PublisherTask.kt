package com.yumdao.daydayup

import com.android.build.gradle.LibraryExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

open class PublisherTask : DefaultTask() {

    //是否完成执行任务
    private var executeFinishFlag: AtomicBoolean = AtomicBoolean(false)

    //检验状态是否通过
    private var checkStatus = false

    private lateinit var publisher: Publisher

    private var currentPublishTaskName: String? = null

    init {
        group = "upload"

        project.run {
            publisher = extensions.getByName(Publisher.NAME) as Publisher

            //动态为该模块引入上传插件
            apply(hashMapOf<String, String>(Pair("plugin", "maven-publish")))
            val publishing = project.extensions.getByType(PublishingExtension::class.java)

            //当前模块是否为android模块，当前只支持android和java模块 非android即java
            val isAndroidModule = project.plugins.hasPlugin("com.android.library")
            val dynamicGenerateSourceTask = dynamicGenerateSourceTask(isAndroidModule)

            afterEvaluate {
                components.forEach {
                    if (isAndroidModule) {
                        //android只注册release的发布，如果需要all/debug或更多其他的可以自定义注册
                        if (it.name == "release") {
                            doRegisterTask(publishing, it.name, it, dynamicGenerateSourceTask)
                        }
                    } else {
                        //Java只注册java的发布，如果需要kotlin或更多其他的可以自定义注册
                        if (it.name == "java") {
                            doRegisterTask(publishing, it.name, it, dynamicGenerateSourceTask)
                        }
                    }
                }
            }
        }
    }


    private fun doRegisterTask(
        publishing: PublishingExtension,
        name: String,
        component: SoftwareComponent,
        withSourceTask: Task?,
    ) {

        currentPublishTaskName = "publish${name}PublicationToMavenRepository"

        publishing.publications { publications ->
            if (publications.findByName(name) == null) {
                publications.create("$name",
                    MavenPublication::class.java) { publication ->
                    publication.groupId = publisher.libGroup
                    publication.artifactId = publisher.libArtifact
                    publication.version = publisher.libVersion

                    withSourceTask?.let {
                        publication.artifact(it)
                    }

                    publication.from(component)
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

    /**
     * 动态生成源码task
     * @param isAndroidModule 是否为android模块
     */
    private fun dynamicGenerateSourceTask(isAndroidModule: Boolean): Task? {

        if (!publisher.withSource) {
            return null
        }

        // 其实taskName叫什么都无所谓，但是处于规范，还是这么命名吧
        val taskName = if (isAndroidModule) {
            "androidSourcesJar"
        } else {
            "sourcesJar"
        }
        //这个地方的api跟脚本中所有不同
        val sourceSetFiles = if (isAndroidModule) {
            //获取build.gradle中的android节点
            val androidSet = project.extensions.getByName("android") as LibraryExtension
            val sourceSet = androidSet.sourceSets
            //获取android节点下的源码目录
            sourceSet.findByName("main")?.java?.srcDirs
        } else {
            //获取java模块中的源码目录
            val plugin = project.convention.getPlugin(JavaPluginConvention::class.java)
            plugin.sourceSets.findByName("main")?.allSource
        }

        //查找或注册源码task
        return project.tasks.findByName(taskName) ?: project.tasks.create(taskName,
            Jar::class.java) {
            it.from(sourceSetFiles)
            it.archiveClassifier.set("sources")
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

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").toLowerCase().contains("windows")
    }

    private fun executeTask() {
        //1、对publisher配置的信息进行基础校验
        //2、把publisher上传到服务器端，做版本重复性校验
        checkStatus = requestCheckVersion()

        //如果前两步都校验通过了，checkStatus设置为true

        val realTaskName =
            project.projectDir.absolutePath
                .removePrefix(project.rootDir.absolutePath)
                .replace(File.separator, ":") + ":$currentPublishTaskName"

        val exeCommand = if (isWindows()) {
            "${project.rootDir}${File.separator}gradlew.bat"
        } else {
            "${project.rootDir}${File.separator}gradlew"
        }


        if (checkStatus) {
            val out = ByteArrayOutputStream()
            //通过命令行的方式进行调用上传maven的task
            project.exec { exec ->
                exec.standardOutput = out
                exec.isIgnoreExitValue = true
                exec.commandLine(
                    exeCommand,
                    realTaskName
                )
            }
            val result = out.toString()
            //TODO 上传maven仓库的判断逻辑还不准确 需要进行优化
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