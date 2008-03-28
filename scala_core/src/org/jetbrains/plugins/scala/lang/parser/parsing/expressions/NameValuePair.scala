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
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

object NameValuePair {
  def parse(builder: PsiBuilder): Boolean = {
    val nameMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Ate val
      }
      case _ => {
        nameMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate id
      }
      case _ => {
        builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
        nameMarker.done(ScalaElementTypes.NAME_VALUE_PAIR)
        return true
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN => {
        builder.advanceLexer //Ate =
      }
      case _ => {
        builder error ScalaBundle.message("assign.expected", new Array[Object](0))
      }
    }
    if (!PrefixExpr.parse(builder)) {
      builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
    }
    nameMarker.done(ScalaElementTypes.NAME_VALUE_PAIR)
    return true
  }
}