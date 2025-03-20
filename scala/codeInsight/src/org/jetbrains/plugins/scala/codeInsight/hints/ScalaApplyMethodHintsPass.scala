package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocQuickInfoGenerator
import org.jetbrains.plugins.scala.extensions.{&, PsiElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

private[codeInsight] trait ScalaApplyMethodHintsPass {
  protected def collectApplyMethodHints(editor: Editor, root: PsiElement): Seq[Hint] = {
    if (!(ScalaHintsSettings.xRayMode && ScalaApplicationSettings.XRAY_SHOW_APPLY_METHOD_HINTS)) return Seq.empty

    root.elements(_.isVisible).collect {
      case ScMethodCall((r: ScReferenceExpression) & ResolvesTo(f: ScFunction), _) if f.name == "apply" && !r.textMatches("apply") =>
        val tooltip = () => ScalaDocQuickInfoGenerator.getQuickNavigateInfo(f, r)
        Hint(Seq(Text("."), Text("apply", navigatable = Some(f), tooltip = tooltip)), r, suffix = true, relatesToPrecedingElement = true)
    }.toSeq
  }
}
