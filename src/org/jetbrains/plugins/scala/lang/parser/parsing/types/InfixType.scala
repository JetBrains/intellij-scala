package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * InfixType ::= CompoundType {id [nl] CompoundType}
 */
object InfixType extends InfixType {
  override protected val componentType = CompoundType
  override protected val errorMessage = ScalaBundle.message("compound.type.expected")
}

trait InfixType {
  protected val componentType: Type
  protected val errorMessage: String

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder, star = false)
  def parse(builder: ScalaPsiBuilder, star: Boolean): Boolean = parse(builder,star,isPattern = false)
  def parse(builder: ScalaPsiBuilder, star: Boolean, isPattern: Boolean): Boolean = {
    var infixTypeMarker = builder.mark
    var markerList = List[PsiBuilder.Marker]() //This list consist of markers for right-associated op
    var count = 0
    markerList = infixTypeMarker :: markerList
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => //wildcard is possible for infix types, like for parameterized. No bounds possible
        val typeMarker = builder.mark()
        builder.advanceLexer()
        typeMarker.done(ScalaElementTypes.WILDCARD_TYPE)
        builder.getTokenText match {
          case "<:" | ">:" =>
            infixTypeMarker.rollbackTo()
            return false
          case _ =>
        }
      case _ =>
        if (!componentType.parse(builder, star, isPattern)) {
          infixTypeMarker.rollbackTo()
          return false
        }
    }
    var assoc: Int = 0  //this mark associativity: left - 1, right - -1
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && (!builder.newlineBeforeCurrentToken) &&
      (!star || builder.getTokenText != "*") && (!isPattern || builder.getTokenText != "|")) {
      count = count+1
      //need to know associativity
      val s = builder.getTokenText
      s.charAt(s.length-1) match {
        case ':' =>
          assoc match {
            case 0  => assoc = -1
            case 1  => builder error ScalaBundle.message("wrong.type.associativity")
            case -1 =>
          }
        case _ =>
          assoc match {
            case 0  => assoc = 1
            case 1  =>
            case -1 => builder error ScalaBundle.message("wrong.type.associativity")
          }
      }
      parseId(builder)
      if (assoc == -1) {
        val newMarker = builder.mark
        markerList = newMarker :: markerList
      }
      if (builder.twoNewlinesBeforeCurrentToken) {
        builder.error(errorMessage)
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER => //wildcard is possible for infix types, like for parameterized. No bounds possible
          val typeMarker = builder.mark()
          builder.advanceLexer()
          typeMarker.done(ScalaElementTypes.WILDCARD_TYPE)
        case _ =>
          if (!componentType.parse(builder, star, isPattern)) builder error errorMessage
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
        infixTypeMarker.drop()
      }
      else {
        markerList.head.drop()
        for (x: PsiBuilder.Marker <- markerList.tail) x.done(ScalaElementTypes.INFIX_TYPE)
      }
    }
    else {
      if (assoc == 1) {
        infixTypeMarker.drop()
      }
      else {
        for (x: PsiBuilder.Marker <- markerList) x.drop()
      }
    }
    true
  }

  protected def parseId(builder: ScalaPsiBuilder, elementType: IElementType = ScalaElementTypes.REFERENCE) {
    val idMarker = builder.mark
    builder.advanceLexer() //Ate id
    idMarker.done(elementType)
  }
}