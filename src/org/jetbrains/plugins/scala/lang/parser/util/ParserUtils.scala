package org.jetbrains.plugins.scala.lang.parser.util

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Top
import com.intellij.psi.tree.IElementType

import com.intellij.lang.PsiBuilder

object ParserUtils {

  def rollForward(builder: PsiBuilder) : Unit = {
    var flag = true
    while ( !builder.eof() && flag){
       builder.getTokenType match{
         case ScalaTokenTypes.tLINE_TERMINATOR => builder.advanceLexer
         case _ => flag = false
       }
    }
  }

  //Write element node
  def eatElement(builder: PsiBuilder, elem: IElementType): Unit = {
    val marker = builder.mark()
    builder.advanceLexer // Ate DOT
    marker.done(elem)
  }

  //Write element node
  def errorToken(builder: PsiBuilder,
                 marker: PsiBuilder.Marker ,
                 msg: String,
                 elem: ScalaElementType): ScalaElementType = {
    builder.error(msg)
    marker.done(elem)
    ScalaElementTypes.WRONGWAY
  }

}