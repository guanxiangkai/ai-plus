/**
 * redis-plus-governance-starter - metrics, observation, and health auto-configuration.
 */
dependencies {
    api(projects.redisPlusGovernance)
    api(projects.redisPlusCacheStarter)

    implementation(libs.bundles.starter.impl.support)
}
