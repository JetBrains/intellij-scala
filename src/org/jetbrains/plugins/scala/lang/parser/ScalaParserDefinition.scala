package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ParserDefinition, com.intellij.lang.PsiParser
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

import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaFile
//import org.jetbrains.plugins.scala.ScalaFileType

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
        var whiteSpaces = new Array[IElementType](2)
        whiteSpaces.update(0, ScalaTokenTypes.tWHITE_SPACE_IN_LINE)
        whiteSpaces.update(1, ScalaTokenTypes.tNON_SIGNIFICANT_NEWLINE)
        val whiteSpaceTokens = TokenSet create( whiteSpaces )
        whiteSpaceTokens
    }

    def getCommentTokens() : TokenSet = {
        var comments = Array(
          ScalaTokenTypes.tCOMMENT,
          ScalaTokenTypes.tBLOCK_COMMENT,
          // New
          ScalaTokenTypes.tCOMMENT_BEGIN,
          ScalaTokenTypes.tCOMMENT_END,
          ScalaTokenTypes.tDOC_COMMENT_BEGIN,
          ScalaTokenTypes.tDOC_COMMENT_END,
          ScalaTokenTypes.tCOMMENT_CONTENT
        )
        val commentTokens = TokenSet create( comments )
        commentTokens
    }

    def createElement( astNode : ASTNode ) : PsiElement = {
       ScalaPsiCreator.createElement( astNode )
    }

    def createFile(fileViewProvider : FileViewProvider) : PsiFile = {
        return new ScalaFile(fileViewProvider);
    }

    def spaceExistanceTypeBetweenTokens(astNode : ASTNode, astNode1 : ASTNode)  : ParserDefinition.SpaceRequirements = {
        throw new UnsupportedOperationException("spaceExistanceTypeBetweenTokens not implemented in org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition");
    }

}
