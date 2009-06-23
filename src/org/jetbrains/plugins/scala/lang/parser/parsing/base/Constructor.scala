package org.jetbrains.plugins.scala.lang.parser.parsing.base

import com.intellij.lang.PsiBuilder
import expressions.ArgumentExprs
import lexer.ScalaTokenTypes
import types.AnnotType

/**
 * @author AlexanderPodkhalyuzin
 */

/*
 * Constr ::= AnnotType {ArgumentExprs}
 */

object Constructor {
  def parse(builder: PsiBuilder): Boolean = parse(builder, false)
  def parse(builder: PsiBuilder, isAnnotation: Boolean): Boolean = {
    val constrMarker = builder.mark
    if (!AnnotType.parse(builder)) {
      constrMarker.drop
      return false
    }
    if (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
      ArgumentExprs parse builder
      while (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS && !isAnnotation) {
        ArgumentExprs parse builder
      }
    }
    constrMarker.done(ScalaElementTypes.CONSTRUCTOR)
    return true
  }
}