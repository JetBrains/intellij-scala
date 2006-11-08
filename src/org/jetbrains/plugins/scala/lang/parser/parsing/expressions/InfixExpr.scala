import scala.collection.mutable.Stack
/**
* @author Ilya Sergey
*/

package org.jetbrains.plugins.scala.lang.parser.parsing.expressions {

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

/*
INFIX EXPRESSION
Default grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

Realized grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

FIRST(InfixExpression) =  PrefixExpression.FIRST

*/
  object InfixExpr {

    // get priority of current operator identifier
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
      if (priority(id1) < priority(id2)) 1
      else if (priority(id1) > priority(id2)) -1
      else if (assoc(id1) == -1) -1
      else 0
    }

    // Associations of operator
    def assoc (id: String): Int = {
      val last = id.charAt(id.length-1)
      last match {
        case ':' => -1   // right
        case _   => +1  // left
      }
    }

    val INFIX_FIRST = PrefixExpr.PREFIX_FIRST

    def parse(builder : PsiBuilder) : ScalaElementType = {

      val markerStack = new Stack[PsiBuilder.Marker]
      val opStack = new Stack[String]
      val marker = builder.mark()
      var result = PrefixExpr parse(builder)

      // Parsing after second operator encountered
      def subParse1() : ScalaElementType = {
        builder.getTokenType match {
          // If an identifier
          case  ScalaTokenTypes.tIDENTIFIER
              | ScalaTokenTypes.tPLUS
              | ScalaTokenTypes.tMINUS
              | ScalaTokenTypes.tTILDA
              | ScalaTokenTypes.tNOT
              | ScalaTokenTypes.tOR
              | ScalaTokenTypes.tSTAR
              | ScalaTokenTypes.tCOLON
              | ScalaTokenTypes.tAND
              | ScalaTokenTypes.tDIV => {
            // current Operation
            val currentOp = builder.getTokenText
            // marker before current Opertaor
            var rbMarker = builder.mark()
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            val res1 = PrefixExpr.parse(builder)
            rbMarker.rollbackTo()
            if (!res1.equals(ScalaElementTypes.WRONGWAY)) {
              // Analyze priority of current and las operators
              if ( !opStack.isEmpty && compare(opStack.top, currentOp) >= 0) {
                markerStack.pop.drop()
              }
              while ( !opStack.isEmpty && compare(opStack.top, currentOp) >= 0 ) {
                opStack.pop
                var tempMarker = markerStack.top.precede()
                markerStack.pop.done(ScalaElementTypes.INFIX_EXPR)
                if (opStack.isEmpty) {
                  markerStack += tempMarker
                } else if ( compare(opStack.top, currentOp) < 0 ) {
                  markerStack += tempMarker                  
                } else tempMarker.drop()
              }
              ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
              val newMarker = builder.mark()
              PrefixExpr.parse(builder)
              markerStack += newMarker
              opStack += currentOp
              subParse1
            } else {
              markerStack.pop.drop()
              while (!opStack.isEmpty) {
                opStack.pop 
                markerStack.pop.done(ScalaElementTypes.INFIX_EXPR)
              }
              ScalaElementTypes.INFIX_EXPR
            }
          }
          case _ => {
            markerStack.pop.drop()
            while (!opStack.isEmpty) {
              opStack.pop
              markerStack.pop.done(ScalaElementTypes.INFIX_EXPR)
            }
            ScalaElementTypes.INFIX_EXPR
          }
        }
      }

      def subParse : ScalaElementType = {
        builder.getTokenType match {
          // If an identifier
          case  ScalaTokenTypes.tIDENTIFIER
              | ScalaTokenTypes.tPLUS
              | ScalaTokenTypes.tMINUS
              | ScalaTokenTypes.tTILDA
              | ScalaTokenTypes.tNOT
              | ScalaTokenTypes.tOR
              | ScalaTokenTypes.tSTAR
              | ScalaTokenTypes.tCOLON
              | ScalaTokenTypes.tDIV
              | ScalaTokenTypes.tAND=> {
            val rollbackMarker = builder.mark() //for rollback
            opStack += builder.getTokenText // operator text to stack
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            ParserUtils.rollForward(builder)

            val newMarker = builder.mark()
            val res = PrefixExpr parse(builder)

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
              ScalaElementTypes.INFIX_EXPR
            }
          }
          case _ => {
            markerStack.pop.drop()
            ScalaElementTypes.INFIX_EXPR
          }
        }
      }

      if (!result.equals(ScalaElementTypes.WRONGWAY)) {
        markerStack += marker
        result = subParse
        result
      }
      else {
        builder.error("Wrong infix expression!")
        marker.done(ScalaElementTypes.INFIX_EXPR)
        result
      }
    }
  }

}