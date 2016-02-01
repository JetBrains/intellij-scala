package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation

/**
 * @author Alexander Podkhalyuzin
 */
object EmptyDcl extends EmptyDcl {
  override protected val annotation = Annotation
}

trait EmptyDcl {
  protected val annotation: Annotation

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder,isMod = true)
  def parse(builder: ScalaPsiBuilder, isMod: Boolean): Boolean = {
    val dclMarker = builder.mark
    if (isMod) {
      while (annotation.parse(builder)) {}
      while (Modifier.parse(builder)) {}
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR |
           ScalaTokenTypes.kTYPE =>
        builder.advanceLexer
        builder.error(ScalaBundle.message("identifier.expected"))
        dclMarker.drop
        return true
      case _ =>
        dclMarker.rollbackTo
        return false
    }
  }
}