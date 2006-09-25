package org.jetbrains.plugins.scala.highlighter;

import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.Lexer;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.HashMap;

/**
 * Author: Ilya Sergey
 * Date: 24.09.2006
 * Time: 14:52:13
 */
public class ScalaSyntaxHighlighter extends SyntaxHighlighterBase {

    static final TokenSet kRESWORDS = TokenSet.create(
            ScalaTokenTypes.kPRINT
    );

    static final TokenSet tNUMBERS = TokenSet.create(
            ScalaTokenTypes.tINTEGER
    );

    static final TokenSet tVARIABLES = TokenSet.create(
            ScalaTokenTypes.tIDENTIFIER
    );

    static final TokenSet tOPS = TokenSet.create(
            ScalaTokenTypes.tASSGN,
            ScalaTokenTypes.tDIV,
            ScalaTokenTypes.tMINUS,
            ScalaTokenTypes.tPLUS,
            ScalaTokenTypes.tSTAR
    );

    static final TokenSet tBRACES = TokenSet.create(
            ScalaTokenTypes.tLBRACKET,
            ScalaTokenTypes.tOPEN_PARENTHIS,
            ScalaTokenTypes.tRBRACKET
    );


    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();

    static {
        fillMap(ATTRIBUTES, kRESWORDS, DefaultHighlighter.KEYWORD);
        fillMap(ATTRIBUTES, tNUMBERS, DefaultHighlighter.NUMBER);
        fillMap(ATTRIBUTES, tVARIABLES, DefaultHighlighter.VARIABLES);
        fillMap(ATTRIBUTES, tOPS, DefaultHighlighter.OPERATION_SIGN);
        fillMap(ATTRIBUTES, tBRACES, DefaultHighlighter.BRACKETS);


//        ATTRIBUTES.put(ScalaTokenTypes.tBAD_CHARACTER, DefaultHighliter.BAD_CHARACTER);

    }


    @NotNull
    public Lexer getHighlightingLexer() {
        return new ScalaLexer();
    }

    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType iElementType) {
        return pack(ATTRIBUTES.get(iElementType));
    }
}
