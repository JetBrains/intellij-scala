package org.jetbrains.plugins.scala.lang.parser.util;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry Naydanov
 * Date: 6/27/12
 */
public abstract class ParserPatcher {

    public static ExtensionPointName<ParserPatcher> EP_NAME = ExtensionPointName.create("org.intellij.scala.scalaParserPatcher");
    private static final ParserPatcher defaultPatcher = new ParserPatcher() {
    };

    public boolean canPatch(@NotNull PsiBuilder builder) {
        return false;
    }

    public boolean parse(@NotNull PsiBuilder builder) {
        return false;
    }

    @NotNull
    public static ParserPatcher getSuitablePatcher(@NotNull PsiBuilder targetBuilder) {
        for (ParserPatcher patcher : EP_NAME.getExtensions()) {
            if (patcher.canPatch(targetBuilder)) return patcher;
        }

        return defaultPatcher;
    }

    public static boolean parseSuitably(@NotNull PsiBuilder targetBuilder) {
        return getSuitablePatcher(targetBuilder)
                .parse(targetBuilder);
    }
}
