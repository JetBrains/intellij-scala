package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

import com.intellij.psi.PsiFile
import com.intellij.lang.ParserDefinition

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassTemplate
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.openapi.util.TextRange

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.CharTable
import com.intellij.lexer.Lexer
import com.intellij.lang.impl.PsiBuilderImpl
//import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi._
import com.intellij.psi.impl.source.CharTableImpl

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 14:11:40
* To change this template use File | Settings | File Templates.
*/

/*
 * AnnotationExpr ::= Constr [[nl] '{' {NameValuePair} '}']
 */

object AnnotationExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val annotExprMarker = builder.mark
    if (!Constructor.parse(builder)) {
      annotExprMarker.drop
      return false
    }
    val rollbackMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        if (LineTerminator(builder.getTokenText)) {
          builder.advanceLexer
        }
        else {
          rollbackMarker.drop
          annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
          return true
        }
      }
      case _ => 
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer //Ate }
        while (NameValuePair.parse(builder)) {}
        builder.getTokenType match {
          case ScalaTokenTypes.tRBRACE => {
            builder.advanceLexer
          }
          case _ => {
            builder error ScalaBundle.message("rbrace.expected", new Array[Object](0))
          }
        }
        rollbackMarker.drop
        annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
        return true
      }
      case _ => {
        rollbackMarker.rollbackTo
        annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
        return true
      }
    }
  }
}