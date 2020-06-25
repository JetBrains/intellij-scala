package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * AnnotationExpr ::= Constr [[nl] '{' {NameValuePair} '}']
 */
object AnnotationExpr {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val annotExprMarker = builder.mark
    if (!Constructor.parse(builder, isAnnotation = true)) {
      annotExprMarker.drop()
      return false
    }
    
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        if (builder.twoNewlinesBeforeCurrentToken) {
          annotExprMarker.done(ScalaElementType.ANNOTATION_EXPR)
          return true
        }
        
        builder.advanceLexer() //Ate }
        builder.enableNewlines()
        
        def foo(): Unit = {
          while (NameValuePair.parse(builder)) {
            builder.getTokenType match {
              case ScalaTokenTypes.tCOMMA => builder.advanceLexer()
              case _ =>
            }
            while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
              builder.error(ScalaBundle.message("wrong.annotation.expression"))
              builder.advanceLexer()
            }
          }
        }
        
        ParserUtils.parseLoopUntilRBrace(builder, foo _)
        builder.restoreNewlinesState()
        annotExprMarker.done(ScalaElementType.ANNOTATION_EXPR)
        
        true
      case _ =>
        annotExprMarker.done(ScalaElementType.ANNOTATION_EXPR)
        true
    }
  }
}