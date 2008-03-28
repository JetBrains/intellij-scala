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

/*
 * Annmotation ::= '@' AnnotationExpr [nl]
 */

object Annotation {
  def parse(builder: PsiBuilder): Boolean = {
    val annotMarker = builder.mark
    builder.getTokenText match {
      case "@" => {
        builder.advanceLexer //Ate @
      }
      case _ => {
        annotMarker.drop
        return false
      }
    }
    if (!AnnotationExpr.parse(builder)) {
      builder error ScalaBundle.message("wrong.annotation.expression", new Array[Object](0))
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        if (LineTerminator(builder.getTokenText)) builder.advanceLexer
      }
      case _ => {}
    }
    annotMarker.done(ScalaElementTypes.ANNOTATION)
    return true
  }
}