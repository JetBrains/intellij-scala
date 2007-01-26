package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable.Stack

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

/*
 Template for infix expressions and compoite patterns parsing
 @param elemType: Type of returning element
 @param parseBase: parse function for base of recursion
*/
abstract class InfixTemplate(val elemType: ScalaElementType,
                    val parseBase: PsiBuilder => ScalaElementType) {

  /* Defines priority of operator */
  def priority(id: String) : Int = {
    id.charAt(0) match {
      case '~' | '#' | '@' | '$' | '?' | '\\'     => 0
      case '*' | '/' | '%'                        => 1
      case '+' | '-'                              => 2
      case ':'                                    => 3
      case '=' | '!'                              => 4
      case '<' | '>'                              => 5
      case '&'                                    => 6
      case '^'                                    => 7
      case '|'                                    => 8
      case _                                      => 9
    }
  }

  // compares two operators
  def compare (id1: String, id2: String): Int = {
    if (priority(id1) == priority(id2) &&
        assoc(id1) != assoc(id2))
      return 0

    if (priority(id1) < priority(id2)) 1        //  a * b + c  =((a * b) + c)
    else if (priority(id1) > priority(id2)) -1  //  a + b * c = (a + (b * c))
    else if (assoc(id1) == -1) -1
    else 1
  }

  // Associations of operator
  def assoc (id: String): Int = {
    val last = id.charAt(id.length-1)
    last match {
      case ':' => -1   // right
      case _   => +1  // left
    }
  }

  def parse(builder : PsiBuilder) : ScalaElementType = {

    val markerStack = new Stack[PsiBuilder.Marker]
    val opStack = new Stack[String]
    val marker = builder.mark()

    var result = parseBase(builder)

    // Parsing after second operator encountered
    def subParse1 : ScalaElementType = {
      builder.getTokenType match {
        // If an identifier
        case  ScalaTokenTypes.tIDENTIFIER
            if (!( builder.getTokenText.equals("|") &&
                   elemType==ScalaElementTypes.PATTERN3 )) => {
          // current Operation
          val currentOp = builder.getTokenText
          // marker before current Opertaor
          var rbMarker = builder.mark()
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

          /*Attention!*/

          // NEW 04.12.06
          if (elemType.equals(ScalaElementTypes.INFIX_EXPR))
            ParserUtils.rollForward(builder)

          var res1 = parseBase(builder)

          rbMarker.rollbackTo()
          if (!res1.equals(ScalaElementTypes.WRONGWAY)) {
            // Analyze priority of current and las operators
            if ( !opStack.isEmpty && compare(opStack.top, currentOp) > 0) {
              markerStack.pop.drop()
            }
            while ( !opStack.isEmpty && compare(opStack.top, currentOp) > 0 ) {
              opStack.pop
              var tempMarker = markerStack.top.precede()
              markerStack.pop.done(elemType)
              if (opStack.isEmpty) {
                markerStack += tempMarker
              } else if ( compare(opStack.top, currentOp) < 0 ) {
                markerStack += tempMarker
              } else tempMarker.drop()
            }
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            val newMarker = builder.mark()

            // NEW 04.12.06
            if (elemType.equals(ScalaElementTypes.INFIX_EXPR))
              ParserUtils.rollForward(builder)

            parseBase(builder)
            markerStack += newMarker
            opStack += currentOp
            subParse1
          } else {
            markerStack.pop.drop()
            while (!opStack.isEmpty) {
              opStack.pop
              markerStack.pop.done(elemType)
            }
            elemType
          }
        }
        case _ => {
          markerStack.pop.drop()
          while (!opStack.isEmpty) {
            opStack.pop
            markerStack.pop.done(elemType)
          }
          elemType
        }
      }
    }

    def subParse : ScalaElementType = {

//        ParserUtils.rollForward(builder)
      builder.getTokenType match {
        // If an identifier
        case  ScalaTokenTypes.tIDENTIFIER
            if (!( builder.getTokenText.equals("|") &&
                   elemType==ScalaElementTypes.PATTERN3 )) => {

          val rollbackMarker = builder.mark() //for rollback
          opStack += builder.getTokenText // operator text to stack
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
          if (elemType.equals(ScalaElementTypes.INFIX_EXPR))
            ParserUtils.rollForward(builder)

          val newMarker = builder.mark()
          /*Attention!*/
          //var res = PrefixExpr parse(builder)
//            ParserUtils.rollForward(builder)
          var res = parseBase(builder)

          // if  PE1 op PE2 ....
          if (!res.equals(ScalaElementTypes.WRONGWAY)) {
            rollbackMarker.drop()
            markerStack += newMarker
            // May be next operator encountered
            subParse1
          // else only PE1...
          } else {
            newMarker.drop()
            rollbackMarker.rollbackTo()
            opStack.pop

            markerStack.pop.drop()
            elemType
          }
        }
        case _ => {
          markerStack.pop.drop()
          //elemType
          if (elemType.equals(ScalaElementTypes.PATTERN3)) elemType
          else result
        }
      }
    }
    if (!result.equals(ScalaElementTypes.WRONGWAY)) {

      /* For simple patterns */
      if(ScalaElementTypes.SIMPLE_PATTERN.equals(result) &&
         ScalaTokenTypes.tFUNTYPE.equals(builder.getTokenType) ){
//          ParserUtils.rollForward(builder)
        marker.drop()
        elemType
      } else {
        markerStack += marker
        result = subParse
        result
      }
    }
    else {
      builder.error("Wrong infix expression!")
      marker.done(elemType)
      result
    }
  }
}
