package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{AccessModifierRenderer, ModifiersRenderer}

private [documentationProvider] object WithHtmlPsiLink extends AccessQualifierRenderer {
  val renderer: ModifiersRenderer = new ModifiersRenderer(new AccessModifierRenderer(WithHtmlPsiLink))

  override def renderQualifier(modifier: ScAccessModifier): String =
    if (modifier.isThis) "this"
    else {
      val res = for {
        id        <- modifier.idText
        qualifier <- resolveAccessQualifier(modifier)
      } yield HtmlPsiUtils.psiElementLink(qualifier, id)
      res.getOrElse("")
    }

  private def resolveAccessQualifier(modifier: ScAccessModifier): Option[String] =
    modifier.getReference match {
      case ResolvesTo(element) =>
        element match {
          case clazz: PsiClass => Some(clazz.qualifiedName)
          case _ => None
        }
      case _ => None
    }
}