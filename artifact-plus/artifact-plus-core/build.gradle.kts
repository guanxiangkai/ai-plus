import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.process.CommandLineArgumentProvider

dependencies {
    // 混淆器只作为测试用外部进程工具，不链接或打入本模块。
    testRuntimeOnly(libs.proguard)
}

// 独立验签入口只使用 JDK，不需要混淆器或业务依赖。
tasks.named<Jar>("jar") {
    manifest { attributes["Main-Class"] = "io.github.guanxiangkai.artifact.plus.signing.VerifyArtifact" }
}

// 测试在独立 JVM 启动真实 ProGuard；只传工具路径，不传业务或签名秘密。
abstract class ObfuscatorTestArguments : CommandLineArgumentProvider {
    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection
    override fun asArguments() = listOf("-Dartifact.test.obfuscatorClasspath=${toolClasspath.asPath}")
}

val obfuscatorTestClasspath = configurations.testRuntimeClasspath
tasks.named<Test>("test") {
    jvmArgumentProviders.add(objects.newInstance<ObfuscatorTestArguments>().apply {
        toolClasspath.from(obfuscatorTestClasspath)
    })
}
