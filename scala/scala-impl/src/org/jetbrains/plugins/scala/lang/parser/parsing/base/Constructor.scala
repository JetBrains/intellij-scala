package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{AnnotType, SimpleType}

/**
 * @author AlexanderPodkhalyuzin
 */

/*
 * Constr ::= AnnotType {ArgumentExprs}
 */
object Constructor extends Constructor {
  override protected def argumentExprs = ArgumentExprs
  override protected def annotType = AnnotType
  override protected def simpleType = SimpleType
}

trait Constructor {
  protected def argumentExprs: ArgumentExprs
  protected def annotType: AnnotType
  protected def simpleType: SimpleType

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder, isAnnotation = false)
  
  def parse(builder: ScalaPsiBuilder, isAnnotation: Boolean): Boolean = {
    val constrMarker = builder.mark
    val latestDoneMarker = builder.getLatestDoneMarker
    val annotationAllowed = latestDoneMarker == null || 
      (latestDoneMarker.getTokenType != ScalaElementTypes.TYPE_GENERIC_CALL && 
        latestDoneMarker.getTokenType != ScalaElementTypes.MODIFIERS && 
        latestDoneMarker.getTokenType != ScalaElementTypes.TYPE_PARAM_CLAUSE)

    if ((!isAnnotation && !annotType.parse(builder, isPattern = false, multipleSQBrackets = false)) ||
      (isAnnotation && !simpleType.parse(builder, isPattern = false))) {
      constrMarker.drop()
      return false
    }
    
    if (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
      if (!builder.newlineBeforeCurrentToken)
        argumentExprs parse builder
      while (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS && (!isAnnotation || annotationAllowed) && !builder.newlineBeforeCurrentToken) {
        argumentExprs parse builder
      }
    }
    constrMarker.done(ScalaElementTypes.CONSTRUCTOR)
    true
  }
}