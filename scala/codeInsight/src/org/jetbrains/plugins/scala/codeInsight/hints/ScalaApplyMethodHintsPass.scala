package org.jetbrains.plugins.scala.codeInsight.hints

import org.jetbrains.plugins.scala.annotator.hints.Hint.HintPosition
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocQuickInfoGenerator
import org.jetbrains.plugins.scala.lang.psi.api.InvocationDetails
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

private[codeInsight] trait ScalaApplyMethodHintsPass {
  protected def showApplyMethodHints: Boolean =
    ScalaHintsSettings.xRayMode && ScalaApplicationSettings.XRAY_SHOW_APPLY_METHOD_HINTS

  protected def applyMethodHints(invocation: InvocationDetails): Seq[Hint] = {
    if (!showApplyMethodHints || !invocation.isApply) return Seq.empty

    (for {
      result <- invocation.target
      function <- result.element match {
        case f: ScFunction if f.isApplyMethod => Some(f)
        case _ => None
      }
      receiver <- invocation.thisExpr
    } yield {
      val tooltip = () => ScalaDocQuickInfoGenerator.getQuickNavigateInfo(function, invocation.origin)
      Hint(
        Seq(Text("."), Text(function.name, navigatable = Some(function), tooltip = tooltip)),
        receiver,
        position = HintPosition.AfterElement,
        relatesToPrecedingElement = true
      )
    }).toSeq
  }
}
