dependencies {
    // PowerJob 5.1.2 尚未发布新版 SDK；在保持 API 兼容的前提下覆盖其老旧传递栈。
    api(platform(libs.vertx.stack.depchain))
    api(libs.powerjob.worker.starter)
    implementation(projects.webPlusCore)

    constraints {
        api(libs.scala.library) {
            because("修复 PowerJob 传递的 Scala 标准库漏洞")
        }
        api(libs.guava) {
            because("修复 PowerJob 传递的 Guava 漏洞")
        }
    }
}
