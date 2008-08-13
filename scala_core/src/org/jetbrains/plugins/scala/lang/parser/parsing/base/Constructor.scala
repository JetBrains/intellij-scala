package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

/**
 * @author AlexanderPodkhalyuzin
 */

/*
 * Constr ::= AnnotType {ArgumentExprs}
 */

object ParentConstructor {
  def parse(builder: PsiBuilder): Boolean = {
    val constrMarker = builder.mark
    if (!AnnotType.parse(builder)) {
      builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
      constrMarker.rollbackTo
      return false
    }
    if (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
      while (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
        ArgumentExprs parse builder
      }
    }
    else builder.mark.done(ScalaElementTypes.ARG_EXPRS)
    constrMarker.done(ScalaElementTypes.CONSTRUCTOR)
    return true
  }
}