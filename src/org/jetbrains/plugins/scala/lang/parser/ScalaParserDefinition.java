package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Author: Ilya Sergey
 * Date: 25.09.2006
 * Time: 14:23:22
 */
public class ScalaParserDefinition implements ParserDefinition {
    @NotNull                 
    public Lexer createLexer(Project project) {
        throw new UnsupportedOperationException("createLexer not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    public PsiParser createParser(Project project) {
        throw new UnsupportedOperationException("createParser not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    public IFileElementType getFileNodeType() {
        throw new UnsupportedOperationException("getFileNodeType not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    @NotNull
    public TokenSet getWhitespaceTokens() {
        throw new UnsupportedOperationException("getWhitespaceTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    @NotNull
    public TokenSet getCommentTokens() {
        throw new UnsupportedOperationException("getCommentTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    @NotNull
    public PsiElement createElement(ASTNode astNode) {
        throw new UnsupportedOperationException("createElement not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    public PsiFile createFile(FileViewProvider fileViewProvider) {
        throw new UnsupportedOperationException("createFile not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode astNode, ASTNode astNode1) {
        throw new UnsupportedOperationException("spaceExistanceTypeBetweenTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }
}
