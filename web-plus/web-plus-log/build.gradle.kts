import org.gradle.api.plugins.jvm.JvmTestSuite

dependencies {
    api(projects.webPlusCore)
    api(libs.micrometer.tracing)
    implementation(libs.bundles.log.runtime)
    compileOnly(libs.bundles.log.compileOnly)
    compileOnly(plusModule("jpa-plus-core"))
    compileOnly(plusModule("jpa-plus-audit"))
    compileOnly(plusModule("redis-plus-starter"))
    testImplementation(libs.bundles.log.compileOnly)
    testImplementation(plusModule("jpa-plus-core"))
    testImplementation(plusModule("jpa-plus-audit"))
    testImplementation(plusModule("redis-plus-starter"))
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.spring.boot.webclient)
    annotationProcessor(libs.spring.boot.configuration.processor)
}

testing {
    suites {
        val runtimeConsumer by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(libs.spring.boot.starter.webflux)
                implementation(libs.spring.boot.starter.test)
            }
            targets.configureEach {
                testTask.configure {
                    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("runtimeConsumer"))
}
