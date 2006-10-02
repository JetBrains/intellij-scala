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
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer;

/**
 * Author: Ilya Sergey
 * Date: 25.09.2006
 * Time: 14:23:22
 */
class ScalaParserDefinition extends ParserDefinition {

    public Lexer createLexer(Project project) {
        return new ScalaLexer();
    }

    public PsiParser createParser(Project project) {
        return new ScalaParser();
    }

    public IFileElementType getFileNodeType() {
        throw new UnsupportedOperationException("getFileNodeType not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }


    public TokenSet getWhitespaceTokens() {
        throw new UnsupportedOperationException("getWhitespaceTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }


    public TokenSet getCommentTokens() {
        throw new UnsupportedOperationException("getCommentTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }


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
