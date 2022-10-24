package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, errorIf2_13}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition

private[annotator] object ScMacroDefAnnotator extends ElementAnnotator[ScMacroDefinition] {
  override def annotate(
    element:   ScMacroDefinition,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit =
    if (element.returnTypeElement.isEmpty)
      errorIf2_13(element.nameId, ScalaBundle.message("macro.defs.must.have.explicit.return.type"))
}
