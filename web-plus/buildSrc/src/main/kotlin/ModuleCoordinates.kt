import org.gradle.api.Project
import java.util.Properties

private const val PLUS_GROUP = "io.github.guanxiangkai"

/**
 * 返回同仓 Plus 模块的 Maven 坐标。
 *
 * 版本统一读取 monorepo 根目录的权威版本文件，避免 Web Plus 再维护一份重复版本。
 *
 * @param module 模块 artifactId，同时也是版本文件中的键。
 * @return 完整的 `groupId:artifactId:version` 坐标。
 */
fun Project.plusModule(module: String): String {
    val versionsFile = rootProject.file("../gradle/module-versions.properties")
    val versions = Properties().apply {
        versionsFile.inputStream().use(::load)
    }
    val version = requireNotNull(versions.getProperty(module)) {
        "gradle/module-versions.properties 缺少模块版本: $module"
    }
    return "$PLUS_GROUP:$module:$version"
}
