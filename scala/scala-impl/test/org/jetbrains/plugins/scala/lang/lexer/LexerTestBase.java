package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.ex.util.ValidatingLexerWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;

abstract public class LexerTestBase extends ScalaFileSetTestCase {

    protected LexerTestBase(@NotNull @NonNls String dataPath) {
        super(dataPath);
    }

    @NotNull
    protected Lexer createLexer(@NotNull Project project) {
        return LanguageParserDefinitions.INSTANCE
                .forLanguage(getLanguage())
                .createLexer(project);
    }

    protected void onToken(@NotNull Lexer lexer,
                           @NotNull IElementType tokenType,
                           @NotNull StringBuilder builder) {
        builder.append(tokenType.toString());
        printTokenRange(lexer.getTokenStart(), lexer.getTokenEnd(), builder);
        printTokenText(lexer.getTokenText(), builder);
        builder.append('\n');
    }

    protected void onEof(@NotNull Lexer lexer,
                         @NotNull StringBuilder builder) {
    }

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
    protected String transform(@NotNull String testName,
                               @NotNull String fileText,
                               @NotNull Project project) {
        final Lexer lexer = new ValidatingLexerWrapper(createLexer(project));
        lexer.start(fileText);

        StringBuilder builder = new StringBuilder();

        IElementType tokenType = lexer.getTokenType();
        while (tokenType != null) {
            onToken(lexer, tokenType, builder);

            lexer.advance();
            tokenType = lexer.getTokenType();
        }

        onEof(lexer, builder);
        return builder.toString();
    }

    private static void printTokenText(@NotNull String tokenText,
                                       @NotNull StringBuilder builder) {
        builder.append(' ').append('{')
                .append(tokenText)
                .append('}');
    }
}