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
object AnnotationExpr extends AnnotationExpr {
  override protected val constructor = Constructor
  override protected val nameValuePair = NameValuePair
}

trait AnnotationExpr {
  protected val constructor: Constructor
  protected val nameValuePair: NameValuePair

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val annotExprMarker = builder.mark
    if (!constructor.parse(builder, isAnnotation = true)) {
      annotExprMarker.drop()
      return false
    }
    
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        if (builder.twoNewlinesBeforeCurrentToken) {
          annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
          return true
        }
        
        builder.advanceLexer() //Ate }
        builder.enableNewlines
        
        def foo() {
          while (nameValuePair.parse(builder)) {
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
        
        ParserUtils.parseLoopUntilRBrace(builder, foo)
        builder.restoreNewlinesState
        annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
        
        true
      case _ =>
        annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
        true
    }
  }
}