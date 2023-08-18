package org.jetbrains.plugins.scala.structureView

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{ParameterRendererLike, ParametersRenderer}

package object element {

  private[structureView]
  val FromStubsParameterRenderer = new ParametersRenderer(RenderOnlyParameterTypeFromStub)

  private[structureView]
  object RenderOnlyParameterTypeFromStub extends ParameterRendererLike {

    override def render(buffer: StringBuilder, param: ScParameter): Unit = {
      val paramTypeText = param.paramType match {
        case Some(pt) => pt.getText
        case _        => "AnyRef"
      }
      buffer.append(paramTypeText)
    }
  }
}
