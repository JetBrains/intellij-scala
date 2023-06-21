package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import com.intellij.psi.PsiModifier
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
  }

  def simpleTextHtmlEscaped(modifier: ScAccessModifier): String =
    new AccessModifierRenderer(new AccessQualifierRenderer.SimpleText(TextEscaper.Html)).render(modifier)
}
