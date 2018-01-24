package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder.Marker
import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Literal
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.SIMPLE_TYPE
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object SimpleType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType {
  override protected def typeArgs = TypeArgs
  override protected def types = ArgTypes
  override protected def literal = Literal

  override protected def rollbackCase(builder: ScalaPsiBuilder, simpleMarker: Marker) = builder.getTokenType match {
    case _ =>
      if (Refinement.parse(builder) || Literal.parse(builder)) {
        simpleMarker.done(SIMPLE_TYPE)
        true
      } else {
        super.rollbackCase(builder, simpleMarker)
      }
  }
}
