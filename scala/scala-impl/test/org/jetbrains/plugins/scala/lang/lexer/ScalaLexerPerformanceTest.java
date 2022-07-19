package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.PlatformTestUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

public class ScalaLexerPerformanceTest extends TestCase {
    @NotNull
    public static Test suite() {
        return new ScalaLexerTestBase("/lexer/performance") {
            @NotNull
            @Override
            protected String transform(@NotNull String testName,
                                       @NotNull String fileText,
                                       @NotNull Project project) {
                String[] result = new String[1];

                PlatformTestUtil.assertTiming(
                        "Lexer performance test",
                        1000,
                        () -> result[0] = super.transform(testName, fileText, project)
                );

                return result[0];
            }

            @Override
            protected void onToken(@NotNull Lexer lexer,
                                   @NotNull IElementType tokenType,
                                   @NotNull StringBuilder builder) {
            }
        };
    }
}
