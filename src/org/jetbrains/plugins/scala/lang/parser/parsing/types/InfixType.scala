package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * InfixType ::= CompoundType {id [nl] CompoundType}
 */

object InfixType {
  def parse(builder: PsiBuilder): Boolean = parse(builder, false)
  def parse(builder:PsiBuilder, star: Boolean): Boolean = parse(builder,star,false)
  def parse(builder: PsiBuilder, star: Boolean, isPattern: Boolean): Boolean = {
    var infixTypeMarker = builder.mark
    var markerList = List[PsiBuilder.Marker]() //This list consist of markers for right-associated op
    var count = 0
    markerList = infixTypeMarker :: markerList
    if (!CompoundType.parse(builder)) {
      infixTypeMarker.rollbackTo
      return false
    }
    var assoc: Int = 0  //this mark associativity: left - 1, right - -1
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && (!star || builder.getTokenText != "*") && (!isPattern || builder.getTokenText != "|")) {
      count = count+1
      //need to know associativity
      val s = builder.getTokenText
      s.charAt(s.length-1) match {
        case ':' => {
          assoc match {
            case 0 => assoc = -1
            case 1 => {
              builder error ScalaBundle.message("wrong.type.associativity")
            }
            case -1 => {}
          }
        }
        case _ => {
          assoc match {
            case 0 => assoc = 1
            case 1 => {}
            case -1 => {
              builder error ScalaBundle.message("wrong.type.associativity")
            }
          }
        }
      }
      val idMarker = builder.mark
      builder.advanceLexer //Ate id
      idMarker.done(ScalaElementTypes.REFERENCE)
      if (assoc == -1) {
        val newMarker = builder.mark
        markerList = newMarker :: markerList
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => {
          if (!LineTerminator(builder.getTokenText)) {
            builder error ScalaBundle.message("compound.type.expected")
          }
          else {
            builder.advanceLexer //Ale nl
          }
        }
        case _ => {}
      }
      if (!CompoundType.parse(builder)) {
        builder error ScalaBundle.message("compound.type.expected")
      }
      if (assoc == 1) {
        val newMarker = infixTypeMarker.precede
        infixTypeMarker.done(ScalaElementTypes.INFIX_TYPE)
        infixTypeMarker = newMarker
      }
    }
    //final ops closing
    if (count>0) {
      if (assoc == 1) {
        infixTypeMarker.drop
      }
      else {
        for (x: PsiBuilder.Marker <- markerList) x.done(ScalaElementTypes.INFIX_TYPE)
      }
    }
    else {
      if (assoc == 1) {
        infixTypeMarker.drop
      }
      else {
        for (x: PsiBuilder.Marker <- markerList) x.drop
      }
    }
    return true
  }
}