package io.github.guanxiangkai.artifact.plus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** 仅接受封闭的保留规则块，不把任意 ProGuard 配置当作可信输入。 */
final class KeepRules {
    private static final int MAXIMUM_BYTES = 1024 * 1024;
    private static final String NAME = "[A-Za-z_$*?][A-Za-z0-9_$.*?]*";
    private static final Pattern HEADER = Pattern.compile(
            "-keep(?:names|classmembers|classmembernames|classeswithmembers|classeswithmembernames)?\\s+"
                    + "(?:@" + NAME + "\\s+)?"
                    + "(?:(?:!?public|!?final|!?abstract|!?synthetic)\\s+)*"
                    + "(?:class|interface|enum|@interface)\\s+!?" + NAME
                    + "(?:\\s*,\\s*!?" + NAME + ")*"
                    + "(?:\\s+(?:extends|implements)\\s+(?:@" + NAME + "\\s+)?" + NAME + ")?\\s*");
    private static final Pattern MEMBERS = Pattern.compile("[A-Za-z0-9_$.*?%!@,;()\\[\\]\\s]*");

    private KeepRules() {}

    static String read(Path path) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAXIMUM_BYTES + 1);
        }
        if (bytes.length > MAXIMUM_BYTES) throw new IOException("保留规则文件过大");
        String rules = new String(bytes, StandardCharsets.UTF_8).replaceAll("(?m)#.*$", "");
        int cursor = 0;
        while (cursor < rules.length()) {
            while (cursor < rules.length() && Character.isWhitespace(rules.charAt(cursor))) cursor++;
            if (cursor == rules.length()) break;
            int open = rules.indexOf('{', cursor);
            int close = open < 0 ? -1 : rules.indexOf('}', open + 1);
            if (open < 0 || close < 0 || !HEADER.matcher(rules.substring(cursor, open)).matches()) {
                throw invalidRules();
            }
            // 只允许 ProGuard 的固定成员占位符；禁止系统属性替换、引用文件和额外选项。
            String members = rules.substring(open + 1, close)
                    .replace("<init>", "init").replace("<fields>", "fields").replace("<methods>", "methods");
            if (!MEMBERS.matcher(members).matches()) throw invalidRules();
            cursor = close + 1;
        }
        return rules;
    }

    private static IOException invalidRules() {
        return new IOException("额外规则只允许带 { } 的 keep 系列保留块；不支持选项修饰符、文件引用或属性替换");
    }
}
