package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.PrecedenceClimbingInfixParsingRule

/*
 * InfixType ::= CompoundType {id [nl] CompoundType}
 */
object InfixType extends InfixType {
  override protected def componentType: Type = CompoundType
  override protected def errorMessage: String = ScalaBundle.message("compound.type.expected")
}

trait InfixType {
  protected def componentType: Type
  @Nls
  protected def errorMessage: String

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder, star = false)
  def parse(builder: ScalaPsiBuilder, star: Boolean): Boolean = parse(builder,star,isPattern = false)
  def parse(builder: ScalaPsiBuilder, star: Boolean, isPattern: Boolean): Boolean = {
    if (builder.isScala3) {
      return parseInScala3(star, isPattern)(builder)
    }
    implicit val b: ScalaPsiBuilder = builder

    var markerList = List.empty[PsiBuilder.Marker] //This list consist of markers for right-associated op

    var infixTypeMarker = builder.mark
    markerList ::= infixTypeMarker

    if (parseInfixWildcardType()) {
      builder.getTokenText match {
        case Bounds.UPPER | Bounds.LOWER =>
          infixTypeMarker.rollbackTo()
          return false
        case _ =>
      }
    } else if (!componentType.parse(builder, star, isPattern)) {
      infixTypeMarker.rollbackTo()
      return false
    }

    var couldBeVarArg = false
    var count = 0
    var assoc: Associativity = Associativity.NoAssociativity

    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && (!builder.newlineBeforeCurrentToken) &&
      (!star || builder.getTokenText != "*") && (!isPattern || builder.getTokenText != "|")) {
      count = count+1
      //need to know associativity
      val s = builder.getTokenText
      couldBeVarArg = if (count == 1 && s == "*") true else false
      
      s.last match {
        case ':' =>
          assoc match {
            case Associativity.NoAssociativity  => assoc = Associativity.Right
            case Associativity.Left  => builder error ScalaBundle.message("wrong.type.associativity")
            case Associativity.Right =>
          }
        case _ =>
          assoc match {
            case Associativity.NoAssociativity  => assoc = Associativity.Left
            case Associativity.Left  =>
            case Associativity.Right => builder error ScalaBundle.message("wrong.type.associativity")
          }
      }
      parseId(builder)
      if (assoc == Associativity.Right) {
        val newMarker = builder.mark
        markerList ::= newMarker
      }
      if (builder.twoNewlinesBeforeCurrentToken) {
        builder.error(errorMessage)
      }
      if (parseInfixWildcardType()) {
        // ok continue
      } else if (componentType.parse(builder, star, isPattern)) {
        couldBeVarArg = false
      } else {
        builder.error(errorMessage)
      }

      if (assoc == Associativity.Left) {
        val newMarker = infixTypeMarker.precede
        infixTypeMarker.done(ScalaElementType.INFIX_TYPE)
        infixTypeMarker = newMarker
      }
    }
    //final ops closing
    if (count>0) {
      if (assoc == Associativity.Left) {
        if (couldBeVarArg && builder.lookBack(ScalaTokenTypes.tIDENTIFIER) && count == 1) {
          infixTypeMarker.rollbackTo()
          parseId(builder)
          return false
        } else infixTypeMarker.drop()
      }
      else {
        markerList.head.drop()
        for (x: PsiBuilder.Marker <- markerList.tail) x.done(ScalaElementType.INFIX_TYPE)
      }
    }
    else {
      if (assoc == Associativity.Left) {
        infixTypeMarker.drop()
      }
      else {
        for (x: PsiBuilder.Marker <- markerList) x.drop()
      }
    }
    true
  }

  protected def parseId(builder: ScalaPsiBuilder, elementType: IElementType = ScalaElementType.REFERENCE): Unit = {
    val idMarker = builder.mark
    builder.advanceLexer() //Ate id
    idMarker.done(elementType)
  }

  //wildcard is possible for infix types, like for parameterized. No bounds possible
  private def parseInfixWildcardType()(implicit builder: ScalaPsiBuilder): Boolean = {
    val typeMarker = builder.mark()
    if (Type.parseWildcardStartToken()) {
      typeMarker.done(ScalaElementType.WILDCARD_TYPE)
      true
    } else {
      typeMarker.drop()
      false
    }
  }

  private def parseInScala3(star: Boolean, isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val infixParsingRule = new PrecedenceClimbingInfixParsingRule {
      override protected def referenceElementType: IElementType = ScalaElementType.REFERENCE
      override protected def infixElementType: IElementType = ScalaElementType.INFIX_TYPE

      override protected def parseFirstOperator()(implicit builder: ScalaPsiBuilder): Boolean =
        if (parseInfixWildcardType()) {
          builder.getTokenText match {
            case Bounds.UPPER | Bounds.LOWER => false
            case _ => true
          }
        } else {
          componentType.parse(builder, star, isPattern)
        }

      override protected def parseOperator()(implicit builder: ScalaPsiBuilder): Boolean =
        parseInfixWildcardType() || componentType.parse(builder, star, isPattern)

      override protected def shouldContinue(implicit builder: ScalaPsiBuilder): Boolean =
        (!isPattern || builder.getTokenText != "|") && super.shouldContinue
    }

    infixParsingRule()
  }

}