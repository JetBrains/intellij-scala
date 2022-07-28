package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/*
 * AnnotationExpr ::= Constr [[nl] '{' {NameValuePair} '}']
 */
object AnnotationExpr extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val annotExprMarker = builder.mark()
    if (!Constructor.parse(isAnnotation = true)) {
      annotExprMarker.drop()
      return false
    }
    
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE if !builder.isScala3 =>
        if (builder.twoNewlinesBeforeCurrentToken) {
          annotExprMarker.done(ScalaElementType.ANNOTATION_EXPR)
          return true
        }
        
        builder.advanceLexer() //Ate }
        builder.enableNewlines()
        
        ParserUtils.parseLoopUntilRBrace() {
          while (NameValuePair()) {
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
        builder.restoreNewlinesState()
        annotExprMarker.done(ScalaElementType.ANNOTATION_EXPR)
        
        true
      case _ =>
        annotExprMarker.done(ScalaElementType.ANNOTATION_EXPR)
        true
    }
  }
}