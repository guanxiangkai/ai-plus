"""在 GitHub Linux CI 使用真实 Maven/Boot 消费者验证本次构建的制品插件。"""

from pathlib import Path
import os
import subprocess
import tempfile
import tomllib
import xml.etree.ElementTree as ET
import zipfile


def run(arguments, *, env=None, expect_success=True, timeout=600):
    """执行有界测试命令，失败保留工具诊断；命令中不传秘密值。"""
    result = subprocess.run(arguments, env=env, capture_output=True, text=True, timeout=timeout)
    if (result.returncode == 0) != expect_success:
        raise RuntimeError(f"验证命令退出码不符合预期: {arguments[0]}\n{result.stdout}\n{result.stderr}")
    return result.stdout


def require(condition, message):
    """验收断言不受 Python 优化开关影响。"""
    if not condition:
        raise RuntimeError(message)


root = Path(__file__).resolve().parents[2]
versions = dict(line.split("=", 1) for line in (root / "gradle/module-versions.properties")
                .read_text().splitlines() if line and not line.startswith("#"))
core = root / "artifact-plus/artifact-plus-core/build/libs" / f"artifact-plus-core-{versions['artifact-plus-core']}.jar"
artifact_catalog = tomllib.loads((root / "artifact-plus/gradle/libs.versions.toml").read_text())
boot_catalog = tomllib.loads((root / "web-plus/gradle/libs.versions.toml").read_text())
boot_version = boot_catalog["versions"]["spring-boot"]
maven_plugin = root / "artifact-plus/artifact-plus-maven-plugin/build/libs" / (
    f"artifact-plus-maven-plugin-{versions['artifact-plus-maven-plugin']}.jar"
)

with zipfile.ZipFile(maven_plugin) as archive:
    descriptor = archive.read("META-INF/maven/plugin.xml").decode("utf-8")
descriptor_xml = ET.fromstring(descriptor)
require(descriptor_xml.findtext("version") == versions["artifact-plus-maven-plugin"],
        "plugin.xml 未写入 Maven 插件最终版本")
require("${moduleVersion}" not in descriptor and "${coreVersion}" not in descriptor
        and "${proguardVersion}" not in descriptor, "plugin.xml 保留了构建版本占位符")
mojo = descriptor_xml.find("./mojos/mojo")
require(mojo is not None, "plugin.xml 缺少 protect Mojo")
require(mojo.findtext("goal") == "protect", "plugin.xml protect goal 不匹配")
require(mojo.findtext("phase") == "verify", "plugin.xml protect phase 不匹配")
require(mojo.findtext("implementation") == "io.github.guanxiangkai.artifact.plus.maven.ProtectMojo",
        "plugin.xml Mojo 实现类不匹配")
parameter_names = {parameter.findtext("name") for parameter in mojo.findall("./parameters/parameter")}
require({"enabled", "obfuscationEnabled", "signingEnabled", "packages", "inputJar", "outputJar",
         "mappingFile", "privateKeyFile", "publicKeyFile", "pluginArtifacts", "project"} <= parameter_names,
        "plugin.xml 缺少关键保护参数注入")
configuration = {element.tag: (element.text or "") for element in mojo.findall("./configuration/*")}
require(configuration.get("enabled") == "${artifact.protection.enabled}"
        and configuration.get("obfuscationEnabled") == "${artifact.obfuscation.enabled}"
        and configuration.get("signingEnabled") == "${artifact.signing.enabled}",
        "plugin.xml 未绑定保护开关属性")
require(configuration.get("pluginArtifacts") == "${plugin.artifacts}"
        and configuration.get("project") == "${project}", "plugin.xml 缺少 Maven 运行时注入")
requirements = {(requirement.findtext("role"), requirement.findtext("field-name"))
                for requirement in mojo.findall("./requirements/requirement")}
require(("org.apache.maven.project.MavenProjectHelper", "projectHelper") in requirements,
        "plugin.xml 缺少 MavenProjectHelper 注入")
dependencies = {(dependency.findtext("groupId"), dependency.findtext("artifactId")): dependency.findtext("version")
                for dependency in descriptor_xml.findall("./dependencies/dependency")}
require(dependencies.get(("io.github.guanxiangkai", "artifact-plus-core")) == versions["artifact-plus-core"],
        "plugin.xml core 依赖版本不匹配")
require(dependencies.get(("com.guardsquare", "proguard-base")) == artifact_catalog["versions"].get("proguard"),
        "plugin.xml ProGuard 依赖版本不匹配")

with tempfile.TemporaryDirectory(prefix="artifact-maven-test-") as directory:
    work = Path(directory)
    repository = work / "repository"
    maven = ["mvn", "-B", "--no-transfer-progress", f"-Dmaven.repo.local={repository}"]
    # 测试专用隔离仓库，不签名、不发布到外部仓库，也不修改开发者的 Maven Local。
    for module in ("artifact-plus-core", "artifact-plus-maven-plugin"):
        folder = root / "artifact-plus" / module
        run(maven + ["org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file",
                     f"-Dfile={folder / 'build/libs' / (module + '-' + versions[module] + '.jar')}",
                     f"-DpomFile={folder / 'build/publications/mavenJava/pom-default.xml'}"])

    # 一次性测试密钥仅存在 CI 临时目录，测试结束销毁；不是用户或发布凭据。
    private_key = work / "test-private.pem"
    public_key = work / "test-public.pem"
    run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072", "-out", str(private_key)])
    private_key.chmod(0o600)
    run(["openssl", "pkey", "-in", str(private_key), "-pubout", "-out", str(public_key)])
    env = dict(os.environ, ARTIFACT_SIGNING_PRIVATE_KEY_FILE=str(private_key),
               ARTIFACT_SIGNING_PUBLIC_KEY_FILE=str(public_key))
    application = work / "consumer"
    source = application / "src/main/java/demo/Application.java"
    source.parent.mkdir(parents=True)
    source.write_text('''package demo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        var context = SpringApplication.run(Application.class, args);
        System.out.println(Marker.value());
        SpringApplication.exit(context);
    }
    private static final class Marker {
        private static String value() { return "artifact-maven-protected-ok"; }
    }
}
''')
    pom = application / "pom.xml"
    pom.write_text(f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>{boot_version}</version><relativePath/></parent>
  <groupId>test.fixture</groupId><artifactId>business</artifactId><version>1.0</version>
  <properties>
    <java.version>25</java.version>
    <artifact.protection.enabled>true</artifact.protection.enabled>
    <artifact.obfuscation.enabled>true</artifact.obfuscation.enabled>
    <artifact.signing.enabled>true</artifact.signing.enabled>
  </properties>
  <dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency></dependencies>
  <build><plugins>
    <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId><executions><execution><goals><goal>repackage</goal></goals></execution></executions></plugin>
    <plugin>
      <groupId>io.github.guanxiangkai</groupId><artifactId>artifact-plus-maven-plugin</artifactId><version>{versions['artifact-plus-maven-plugin']}</version>
      <executions><execution><goals><goal>protect</goal></goals></execution></executions>
      <configuration><packages><package>demo</package></packages></configuration>
    </plugin>
  </plugins></build>
</project>''')
    run(maven + ["-f", str(pom), "install"], env=env)
    artifact = application / "target/protected/business-1.0.jar"
    signature = artifact.with_name(artifact.name + ".sig")
    mapping = artifact.parent / "mapping.txt"
    require(artifact.is_file() and signature.is_file() and mapping.is_file(), "缺少保护输出")
    # 使用独立 JDK 验签 CLI 及 OpenSSL 交叉验证标准 RSA-PSS 格式。
    verify = ["java", "-jar", str(core), str(artifact), str(signature), str(public_key)]
    run(verify)
    run(["openssl", "dgst", "-sha256", "-verify", str(public_key), "-signature", str(signature),
         "-sigopt", "rsa_padding_mode:pss", "-sigopt", "rsa_pss_saltlen:32", str(artifact)])
    require("artifact-maven-protected-ok" in run(["java", "-jar", str(artifact)], timeout=45), "保护后的 Boot 应用启动失败")
    installed = repository / "test/fixture/business/1.0"
    require((installed / "business-1.0-protected.jar").is_file(), "未附加保护 JAR")
    require((installed / "business-1.0-protected.jar.sig").is_file(), "未附加签名")
    with zipfile.ZipFile(artifact) as archive:
        require(not any("mapping.txt" in name or "proguard" in name.lower() for name in archive.namelist()),
                "业务制品包含构建工具或混淆映射")
        nested = [entry for entry in archive.infolist() if entry.filename.startswith("BOOT-INF/lib/") and entry.filename.endswith(".jar")]
        require(nested and all(entry.compress_type == zipfile.ZIP_STORED for entry in nested), "Boot 嵌套 JAR 存储方式不正确")

    with artifact.open("ab") as stream:
        stream.write(b"tampered")
    run(verify, expect_success=False)
    signature.unlink()
    run(verify, expect_success=False)
    # 关闭后不得残留上一次保护结果；缺密钥构建必须失败且不能输出伪保护制品。
    run(maven + ["-f", str(pom), "verify", "-Dartifact.protection.enabled=false"], env=env)
    require(not artifact.exists() and not signature.exists() and not mapping.exists(), "关闭后残留保护输出")
    private_key.unlink()
    run(maven + ["-f", str(pom), "verify"], env=env, expect_success=False)
    require(not artifact.exists() and not signature.exists(), "失败后残留保护输出")

print("Maven/Boot 消费者、签名互通、篡改拒绝和关闭清理验收通过")
