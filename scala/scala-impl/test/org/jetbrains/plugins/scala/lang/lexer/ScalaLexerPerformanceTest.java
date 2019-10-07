package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class ScalaLexerPerformanceTest extends ScalaLexerTestBase {

    ScalaLexerPerformanceTest() {
        super("/lexer/performance");
    }

    @NotNull
    @Override
    protected String transform(@NotNull String testName,
                               @NotNull String fileText,
                               @NotNull Project project) {
        String[] result = new String[1];

        PlatformTestUtil.assertTiming(
                "Lexer performance test",
                1000,
                () -> result[0] = ScalaLexerPerformanceTest.super.transform(testName, fileText, project)
        );

        return result[0];
    }

    @Override
    protected void onToken(@NotNull Lexer lexer,
                           @NotNull IElementType tokenType,
                           @NotNull StringBuilder builder) {
    }

    @NotNull
    public static ScalaLexerPerformanceTest suite() {
        return new ScalaLexerPerformanceTest();
    }
}
