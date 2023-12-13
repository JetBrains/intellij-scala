package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import com.intellij.psi.{PsiClass, PsiModifier, PsiPackage}
import org.jetbrains.plugins.scala.editor.documentationProvider.{HtmlPsiUtils, _}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition.DesugaredTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{AccessModifierRenderer, ModifiersRenderer}

private [documentationProvider] object WithHtmlPsiLink extends AccessQualifierRenderer {
  private val accessModifierRenderer = new AccessModifierRenderer(WithHtmlPsiLink) {
    override def render(modifier: ScAccessModifier): String = {
      val buffer = new StringBuilder
      buffer.appendKeyword(if (modifier.isPrivate) PsiModifier.PRIVATE else PsiModifier.PROTECTED)
      if (modifier.isThis) buffer.append('[').appendKeyword("this") .append(']')
      else {
        val qualifierText = renderQualifier(modifier)
        if (qualifierText.nonEmpty) buffer.append('[').append(qualifierText).append(']')
      }
      buffer.result()
    }
  }

  val modifiersRenderer: ModifiersRenderer = new ModifiersRenderer(accessModifierRenderer) {
    override def render(buffer: StringBuilder, owner: ScModifierListOwner): Unit = {
      val modifierList = owner.getModifierList
      modifierList
        .accessModifier
        .map(accessModifierRenderer.render)
        .foreach { modifierText => buffer.append(modifierText).append(' ') }

      lazy val isGivenElement = owner match {
        case _: ScGiven                 => true
        case DesugaredTypeDefinition(_) => true
        case _                          => false
      }

      ModifiersRenderer
        .modifiers
        .filter(mod => modifierList.hasModifierProperty(mod) && !(mod.contentEquals("implicit") && isGivenElement))
        .foreach { modifier => buffer.appendKeyword(modifier).append(' ') }
    }
  }

  override def renderQualifier(modifier: ScAccessModifier): String = {
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
          case p: PsiPackage => Some(p.getQualifiedName)
          case _ => None
        }
      case _ => None
    }
}