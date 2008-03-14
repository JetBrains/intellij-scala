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
* Date: 03.03.2008
* Time: 18:09:35
* To change this template use File | Settings | File Templates.
*/

object Ascription {
  def parse(builder: PsiBuilder): Boolean = {
    val ascriptionMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON => {
        builder.advanceLexer //Ate :
      }
      case _ => {
        ascriptionMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate _
        builder.getTokenText match {
          case "*" => {
            builder.advanceLexer //Ate *
          }
          case _ => {
            builder error ScalaBundle.message("star.expected", new Array[Object](0))
          }
        }
        ascriptionMarker.done(ScalaElementTypes.ASCRIPTION)
        return true
      }
      case _ => {}
    }
    if (!CompoundType.parse(builder)) {
      var x = 0;
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {x=x+1}
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      if (x==0) builder error ScalaBundle.message("annotation.expected", new Array[Object](0))
    }
    ascriptionMarker.done(ScalaElementTypes.ASCRIPTION)
    return true
  }
}