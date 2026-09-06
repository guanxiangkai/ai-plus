"""在 Linux CI 核验真实生成的 POM 和测试报告，不签名或发布制品。"""

from pathlib import Path
import xml.etree.ElementTree as ET


root = Path(__file__).resolve().parents[2]
versions = dict(
    line.split("=", 1)
    for line in (root / "gradle/module-versions.properties").read_text().splitlines()
    if line and not line.startswith("#")
)
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
group = "io.github.guanxiangkai"

for module, version in versions.items():
    family = module.split("-plus-", 1)[0] + "-plus"
    pom_file = root / family / module / "build/publications/mavenJava/pom-default.xml"
    pom = ET.parse(pom_file).getroot()
    assert pom.findtext("m:groupId", namespaces=namespace) == group, module
    assert pom.findtext("m:artifactId", namespaces=namespace) == module, module
    assert pom.findtext("m:version", namespaces=namespace) == version, module
    dependencies = pom.findall("m:dependencies/m:dependency", namespace)
    for dependency in dependencies:
        if dependency.findtext("m:groupId", namespaces=namespace) == group:
            target = dependency.findtext("m:artifactId", namespaces=namespace)
            actual = dependency.findtext("m:version", namespaces=namespace)
            assert target in versions, f"{module}: 未知内部模块 {target}"
            assert actual == versions[target], f"{module}: {target} 版本 {actual} 不匹配"
    if module == "jpa-plus-query":
        caffeine = [
            dependency
            for dependency in dependencies
            if dependency.findtext("m:groupId", namespaces=namespace)
            == "com.github.ben-manes.caffeine"
            and dependency.findtext("m:artifactId", namespaces=namespace) == "caffeine"
        ]
        assert len(caffeine) == 1, "查询模块必须声明唯一 Caffeine 依赖"
        assert caffeine[0].findtext("m:scope", namespaces=namespace) == "runtime", (
            "Caffeine 必须作为实现依赖发布"
        )
    print(f"POM 已核验: {module}:{version}")

# 检查对应源码的真实 JUnit 报告，防止构建成功但关键回归用例未被发现。
required_suites = {
    "io.github.guanxiangkai.jpa.plus.query.executor.KeysetCursorExtractorTest",
    "io.github.guanxiangkai.jpa.plus.query.plan.MappingPlanCacheTest",
    "io.github.guanxiangkai.jpa.plus.query.plan.MappingPlanCompilerTest",
    "io.github.guanxiangkai.redis.plus.queue.impl.RedisStreamQueueTest",
    "io.github.guanxiangkai.web.plus.web.service.impl.BaseServiceImplTest",
}
seen = set()
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for report in root.glob("*-plus/*/build/test-results/test/TEST-*.xml"):
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, "0"))
    name = suite.get("name")
    if name in required_suites:
        assert int(suite.get("tests", "0")) > 0, f"回归套件未执行: {name}"
        assert int(suite.get("skipped", "0")) == 0, f"回归套件包含跳过用例: {name}"
        seen.add(name)
        print(f"回归套件已核验: {name}, tests={suite.get('tests')}")
assert seen == required_suites, f"缺失回归报告: {sorted(required_suites - seen)}"
assert totals["failures"] == totals["errors"] == 0, totals
print(f"测试报告合计: {totals}")
