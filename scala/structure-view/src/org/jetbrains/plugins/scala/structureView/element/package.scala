package org.jetbrains.plugins.scala.structureView

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{ParameterRendererLike, ParametersRenderer}

package object element {

  private[structureView]
  object FromStubsParameterRenderer extends ParametersRenderer(RenderParameterTypeAndDefaultValuePlaceholderFromStub, shouldRenderImplicitModifier = true) {
    override protected def renderImplicitOrUsingModifier(buffer: StringBuilder, clause: ScParameterClause, shouldRenderImplicitModifier: Boolean): Unit =
      if (shouldRenderImplicitModifier && clause.isImplicitOrUsing) {
        buffer.append("?=> ")
      }
  }

  private[structureView]
  object RenderParameterTypeAndDefaultValuePlaceholderFromStub extends ParameterRendererLike {

    override def render(buffer: StringBuilder, param: ScParameter): Unit = {
      val paramTypeText = param.paramType match {
        case Some(pt) => pt.getText
        case _        => "AnyRef"
      }
      val text = if (param.isDefaultParam) {
        paramTypeText + " = …"
      } else paramTypeText
      buffer.append(text)
    }
  }
}
