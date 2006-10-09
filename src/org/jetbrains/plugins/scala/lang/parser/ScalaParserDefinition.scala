package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lang.ASTNode
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

import org.jetbrains.plugins.scala.lang.parser.ScalaPsiCreator
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaFile
import org.jetbrains.plugins.scala.ScalaFileType

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
        new ScalaParser()
    }
     
    def getFileNodeType() : IFileElementType  = {
        ScalaElementTypes.FILE
    }

    def getWhitespaceTokens() : TokenSet = {
        var whiteSpaces = new Array[IElementType](1)
        whiteSpaces.update(0, ScalaTokenTypes.tWHITE_SPACE)
        val whiteSpaceTokens = TokenSet create( whiteSpaces )
        whiteSpaceTokens
    }

    def getCommentTokens() : TokenSet = {
        var comments = new Array[IElementType](1)
        comments.update(0, ScalaTokenTypes.tCOMMENT)
        val commentTokens = TokenSet create( comments )
        commentTokens
        //throw new UnsupportedOperationException("getCommentTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    def createElement( astNode : ASTNode ) : PsiElement = {
       ScalaPsiCreator.createElement( astNode )
       //throw new UnsupportedOperationException("createFile not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

    def createFile(fileViewProvider : FileViewProvider) : PsiFile = {
        return new ScalaFile(fileViewProvider);
    }

    def spaceExistanceTypeBetweenTokens(astNode : ASTNode, astNode1 : ASTNode)  : ParserDefinition.SpaceRequirements = {
        throw new UnsupportedOperationException("spaceExistanceTypeBetweenTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

}
