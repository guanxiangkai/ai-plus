import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom

/**
 * POM 元数据配置
 *
 * 所有可配置项均从 `gradle.properties` 读取，前缀为 `pom.`。
 */
fun MavenPom.configureWebPlusPom(project: Project, moduleName: String) {

    fun prop(key: String): String =
        project.providers.gradleProperty(key).orNull
            ?: error("gradle.properties 缺少必需属性: $key")

    name.set(moduleName)
    description.set("${prop("pom.description")} :: $moduleName")
    url.set(prop("pom.url"))

    licenses {
        license {
            name.set(prop("pom.license.name"))
            url.set(prop("pom.license.url"))
        }
    }
    developers {
        developer {
            id.set(prop("pom.developer.id"))
            name.set(prop("pom.developer.name"))
        }
    }
    scm {
        url.set(prop("pom.scm.url"))
        connection.set(prop("pom.scm.connection"))
        developerConnection.set(prop("pom.scm.developerConnection"))
    }
}
