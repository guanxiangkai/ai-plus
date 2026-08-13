package io.github.guanxiangkai.redis.plus.autoconfigure;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.github.guanxiangkai.redis.plus", importOptions = DoNotIncludeTests.class)
class RedisPlusArchitectureTest {

    @ArchTest
    static final ArchRule feature_modules_must_not_depend_on_auto_configuration =
            noClasses()
                    .that().resideOutsideOfPackage("io.github.guanxiangkai.redis.plus.autoconfigure..")
                    .should().dependOnClassesThat().resideInAPackage("io.github.guanxiangkai.redis.plus.autoconfigure..");

    @ArchTest
    static final ArchRule core_must_not_depend_on_feature_modules =
            noClasses()
                    .that().resideInAPackage("io.github.guanxiangkai.redis.plus.core..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.github.guanxiangkai.redis.plus.cache..",
                            "io.github.guanxiangkai.redis.plus.datasource..",
                            "io.github.guanxiangkai.redis.plus.enhance..",
                            "io.github.guanxiangkai.redis.plus.governance..",
                            "io.github.guanxiangkai.redis.plus.idempotent..",
                            "io.github.guanxiangkai.redis.plus.lock..",
                            "io.github.guanxiangkai.redis.plus.queue..",
                            "io.github.guanxiangkai.redis.plus.ratelimit..");

    @ArchTest
    static final ArchRule spring_boot_auto_configuration_must_stay_in_starters =
            noClasses()
                    .that().resideOutsideOfPackage("io.github.guanxiangkai.redis.plus.autoconfigure..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.boot.autoconfigure..",
                            "org.springframework.boot.context.properties..");
}
