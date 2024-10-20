package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.Associativity
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

  private val varargStarFollowSet = TokenSet.create(
    ScalaTokenTypes.tRPARENTHESIS,  // def test(x: Int*)       -- standard case
    ScalaTokenTypes.tASSIGN,        // def test(x: Int* = 3)   -- this is allowed by syntax, but not by semantics
    ScalaTokenTypes.tCOMMA,         // def test(x: Int*,)      -- especially with trailing comma
  )

  final def apply(star: Boolean = false, isPattern: Boolean = false, typeVariables: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.isScala3) {
      return parseInScala3(star, isPattern, typeVariables)(builder)
    }

    var markerList = List.empty[PsiBuilder.Marker] //This list consist of markers for right-associated op

    var infixTypeMarker = builder.mark()
    markerList ::= infixTypeMarker

    if (parseInfixWildcardType()) {
      builder.getTokenText match {
        case Bounds.UPPER | Bounds.LOWER =>
          infixTypeMarker.rollbackTo()
          return false
        case _ =>
      }
    } else if (!componentType(star, isPattern)) {
      infixTypeMarker.rollbackTo()
      return false
    }

    var count = 0
    var assoc: Associativity = Associativity.NoAssociativity

    def varargFollows =
      builder.getTokenText == "*" && varargStarFollowSet.contains(builder.lookAhead(1))

    while (
      builder.getTokenType == ScalaTokenTypes.tIDENTIFIER &&
      !builder.newlineBeforeCurrentToken &&
      !(star && varargFollows) &&
      !(isPattern && builder.getTokenText == "|")
    ) {
      count = count+1
      //need to know associativity
      val s = builder.getTokenText

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
      parseId()
      if (assoc == Associativity.Right) {
        val newMarker = builder.mark()
        markerList ::= newMarker
      }
      if (builder.twoNewlinesBeforeCurrentToken) {
        builder.error(errorMessage)
      }
      if (!parseInfixWildcardType() && !componentType(star, isPattern)) {
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
        infixTypeMarker.drop()
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

  protected def parseId(elementType: IElementType = ScalaElementType.REFERENCE)(implicit builder: ScalaPsiBuilder): Unit = {
    val idMarker = builder.mark()
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

  private def parseInScala3(star: Boolean, isPattern: Boolean, typeVariables: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val infixParsingRule = new PrecedenceClimbingInfixParsingRule {
      override protected def referenceElementType: IElementType = ScalaElementType.REFERENCE
      override protected def infixElementType: IElementType = ScalaElementType.INFIX_TYPE
      override protected def isMatchConsideredInfix: Boolean = false

      override protected def parseFirstOperand()(implicit builder: ScalaPsiBuilder): Boolean =
        if (parseInfixWildcardType()) {
          builder.getTokenText match {
            case Bounds.UPPER | Bounds.LOWER => false
            case _ => true
          }
        } else {
          parseTypeVariable() || componentType(star, isPattern)
        }

      override protected def parseOperand()(implicit builder: ScalaPsiBuilder): Boolean =
        parseInfixWildcardType() || parseTypeVariable() || componentType(star, isPattern)

      override protected def shouldContinue(implicit builder: ScalaPsiBuilder): Boolean =
        (!isPattern ||
          typeVariables ||
          builder.getTokenText != "|") &&
          !(builder.features.`new context bounds and givens` && builder.getTokenText == "as") &&
          super.shouldContinue

    private def parseTypeVariable(): Boolean = if (isPattern && typeVariables && builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
        val firstChar = builder.getTokenText.charAt(0)
        if (firstChar != '`' && firstChar.isLower) {
          val typeVariableMarker = builder.mark()
          val identifierMarker = builder.mark()
          builder.advanceLexer()
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tFUNTYPE  =>
              identifierMarker.drop()
              typeVariableMarker.done(ScalaElementType.TYPE_VARIABLE)
              true
            case _ =>
              identifierMarker.rollbackTo()
              typeVariableMarker.drop()
              false
          }
        } else {
          false
        }
      } else {
        false
      }
    }

    infixParsingRule()
  }
}
