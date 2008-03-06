package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 12:55:10
* To change this template use File | Settings | File Templates.
*/

/*
 * InfixType ::= CompoundType {id [nl] CompoundType}
 */

object InfixType {
  def parse(builder: PsiBuilder): Boolean = {
    var infixTypeMarker = builder.mark
    var markerList = List[PsiBuilder.Marker]() //This list consist of markers for right-associated op
    markerList = markerList.::(infixTypeMarker)
    if (!CompoundType.parse(builder)) {
      infixTypeMarker.rollbackTo
      return false
    }
    var assoc: Int = 0  //this mark associativity: left - 1, right - -1
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      //need to know associativity
      val s = builder.getTokenText
      s.charAt(s.length-1) match {
        case ':' => {
          assoc match {
            case 0 => assoc = -1
            case 1 => {
              builder error ScalaBundle.message("wrong.type.associativity", new Array[Object](0))
            }
            case -1 => {}
          }
        }
        case _ => {
          assoc match {
            case 0 => assoc = 1
            case 1 => {}
            case -1 => {
              builder error ScalaBundle.message("wrong.type.associativity", new Array[Object](0))
            }
          }
        }
      }
      builder.advanceLexer //Ate id
      if (assoc == -1) {
        val newMarker = builder.mark
        markerList = markerList.::(newMarker)
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => {
          if (!LineTerminator(builder.getTokenText)) {
            builder error ScalaBundle.message("compound.type.expected", new Array[Object](0))
          }
          else {
            builder.advanceLexer //Ale nl
          }
        }
        case _ => {}
      }
      if (!CompoundType.parse(builder)) {
        builder error ScalaBundle.message("compound.type.expected", new Array[Object](0))
      }
      if (assoc == 1) {
        val newMarker = infixTypeMarker.precede
        infixTypeMarker.done(ScalaElementTypes.INFIX_TYPE)
        infixTypeMarker = newMarker
      }
    }
    //final ops closing
    if (assoc == 1) {
      infixTypeMarker.drop
    }
    else {
      for (x: PsiBuilder.Marker <- markerList) x.done(ScalaElementTypes.INFIX_TYPE)
    }
    return true
  }
}