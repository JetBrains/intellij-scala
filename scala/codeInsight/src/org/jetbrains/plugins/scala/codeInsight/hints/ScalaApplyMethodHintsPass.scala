package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocQuickInfoGenerator
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

private[codeInsight] trait ScalaApplyMethodHintsPass {
  protected def collectApplyMethodHints(editor: Editor, root: PsiElement): Seq[Hint] = {
    if (!(ScalaHintsSettings.xRayMode && ScalaApplicationSettings.XRAY_SHOW_APPLY_METHOD_HINTS)) return Seq.empty

    root.elements(_.isVisible).flatMap {
      case c: ScMethodCall => c.applyOrUpdateElement.map(_.element) match {
        case Some(f: ScFunction) if f.isApplyMethod =>
          val tooltip = () => ScalaDocQuickInfoGenerator.getQuickNavigateInfo(f, c)
          Seq(Hint(Seq(Text("."), Text(f.name, navigatable = Some(f), tooltip = tooltip)), c.getInvokedExpr, suffix = true, relatesToPrecedingElement = true))
        case _ => Seq.empty
      }
      case _ => Seq.empty
    }.toSeq
  }
}
