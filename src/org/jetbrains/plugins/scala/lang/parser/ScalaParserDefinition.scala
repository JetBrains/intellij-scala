package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.lang.parser.ScalaPsiCreator
import com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer


/**
 * Author: Ilya Sergey
 * Date: 25.09.2006
 * Time: 14:23:22
 */
class ScalaParserDefinition extends ParserDefinition {

    def createLexer(project: Project) : Lexer = {
        new ScalaLexer()
    }

    def createParser(project: Project ) : PsiParser = {
        return new ScalaParser();
    }
     
    def getFileNodeType() : IFileElementType  = {
        ScalaElementTypes.FILE
    }

    def getWhitespaceTokens() : TokenSet = {
        throw new UnsupportedOperationException("getWhitespaceTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    def getCommentTokens() : TokenSet = {
        throw new UnsupportedOperationException("getCommentTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    def createElement( astNode : ASTNode ) : PsiElement = {
       //new ScalaPsiCreator().createElement( astNode )
       throw new UnsupportedOperationException("createFile not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    def createFile(fileViewProvider : FileViewProvider) : PsiFile = {
        throw new UnsupportedOperationException("createFile not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    def spaceExistanceTypeBetweenTokens(astNode : ASTNode, astNode1 : ASTNode)  : ParserDefinition.SpaceRequirements = {
        throw new UnsupportedOperationException("spaceExistanceTypeBetweenTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

}
