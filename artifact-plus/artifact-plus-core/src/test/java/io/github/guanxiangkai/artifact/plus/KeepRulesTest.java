package io.github.guanxiangkai.artifact.plus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证配置文本无法跳出允许的成员保留块。 */
class KeepRulesTest {
    @TempDir Path directory;

    @Test
    void acceptsAnnotationsWildcardsAndMemberPlaceholders() throws Exception {
        String rules = """
                # 注释中的 -outjars 不作为指令
                -keep @demo.Tag public class demo.dto.** extends demo.Base { *; }
                -keepclassmembers class demo.Reflection {
                    @demo.Marker private java.lang.String lookup(java.lang.String);
                    public <init>(...);
                    <fields>;
                    <methods>;
                }
                -keepnames interface demo.api.* { public *; }
                """;
        String validated = KeepRules.read(write(rules));
        assertTrue(validated.contains("@demo.Tag"));
        assertTrue(validated.contains("<init>"));
        assertFalse(validated.contains("-outjars"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-keep class demo.A { *; } @extra.pro",
            "-keep class demo.A { *; }\n-include extra.pro",
            "-keep,allowobfuscation class demo.A { *; }",
            "-keep class demo.A { <user.home>; }",
            "-keep class <user.home> { *; }",
            "-keep class demo.A { *; } -outjars replacement.jar",
            "-keep class demo.A { *; } -dontobfuscate",
            "-keep class demo.A { *; } -ke class demo.B { *; }",
            "-keep class demo.A { *; } '-outjars' replacement.jar",
            "-keep class demo.A { { *; } }",
            "-keep class demo.A",
            "@extra.pro"
    })
    void rejectsIncludesPropertiesOptionsAndIncompleteBlocks(String rules) throws Exception {
        Path file = write(rules);
        assertThrows(IOException.class, () -> KeepRules.read(file));
    }

    @Test
    void boundsActualRuleBytesRead() throws Exception {
        Path file = write("#" + "x".repeat(1024 * 1024));
        assertThrows(IOException.class, () -> KeepRules.read(file));
    }

    private Path write(String rules) throws IOException {
        return Files.writeString(directory.resolve("keep.pro"), rules);
    }
}
