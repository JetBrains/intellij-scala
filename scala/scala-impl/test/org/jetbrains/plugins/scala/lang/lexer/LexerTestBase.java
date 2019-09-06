package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */
abstract public class LexerTestBase extends BaseScalaFileSetTestCase {

    protected LexerTestBase(@NotNull String dataPath) {
        super(customOrPropertyPath(dataPath));
    }

    @NotNull
    protected abstract Lexer createLexer();

    protected void printTokenRange(int tokenStart, int tokenEnd,
                                   @NotNull StringBuilder builder) {
        builder.append(':').append(' ').append('[')
                .append(tokenStart)
                .append(',').append(' ')
                .append(tokenEnd)
                .append(']').append(',');
    }

    @NotNull
    @Override
    public String transform(@NotNull String testName, @NotNull String[] data) {
        Lexer lexer = createLexer();
        lexer.start(data[0]);

        StringBuilder builder = new StringBuilder();

        IElementType tokenType = lexer.getTokenType();
        while (tokenType != null) {
            builder.append(tokenType.toString());
            printTokenRange(lexer.getTokenStart(), lexer.getTokenEnd(), builder);
            printTokenText(lexer.getTokenText(), builder);

            lexer.advance();
            tokenType = lexer.getTokenType();

            if (tokenType != null) {
                builder.append('\n');
            }
        }

        return builder.toString();
    }

    private static String customOrPropertyPath(@NotNull String dataPath) {
        String pathProperty = System.getProperty("path");
        return pathProperty != null ?
                pathProperty :
                dataPath;
    }

    private static void printTokenText(@NotNull String tokenText,
                                       @NotNull StringBuilder builder) {
        builder.append(' ').append('{')
                .append(tokenText)
                .append('}');
    }
}