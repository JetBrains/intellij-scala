package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import com.intellij.psi.{PsiClass, PsiModifier}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.HtmlPsiUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer

class AccessModifierRenderer(
  accessQualifierRenderer: AccessQualifierRenderer
) {

  def render(modifier: ScAccessModifier): String = {
    val modifierText  = if (modifier.isPrivate) PsiModifier.PRIVATE else PsiModifier.PROTECTED
    val qualifierText = accessQualifierRenderer.renderQualifier(modifier)
    modifierText + (if (qualifierText.nonEmpty) s"[$qualifierText]" else "")
  }
}

object AccessModifierRenderer {

  trait AccessQualifierRenderer {
    def renderQualifier(modifier: ScAccessModifier): String
  }
  object AccessQualifierRenderer {

    class SimpleText(
      textEscaper: TextEscaper = TextEscaper.Noop
    ) extends AccessQualifierRenderer {
      override def renderQualifier(modifier: ScAccessModifier): String =
        if (modifier.isThis) textEscaper.escape("this")
        else modifier.idText.map(textEscaper.escape).getOrElse("")
    }

    object WithHtmlPsiLink extends AccessQualifierRenderer {

      override def renderQualifier(modifier: ScAccessModifier): String =
        if (modifier.isThis) "this" else {
          val res = for {
            id <- modifier.idText
            qualifier <- resolveAccessQualifier(modifier, id)
          } yield HtmlPsiUtils.psiElementLink(qualifier, id)
          res.getOrElse("")
        }

      private def resolveAccessQualifier(modifier: ScAccessModifier, idText: String): Option[String] =
        modifier.getReference match {
          case ResolvesTo(element) => element match {
            case clazz: PsiClass  => Some(clazz.qualifiedName)
            case _                => None
          }
          case _ => None
        }
    }
  }
}
