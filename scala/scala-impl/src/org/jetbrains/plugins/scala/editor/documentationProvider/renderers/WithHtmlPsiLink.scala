package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import com.intellij.psi.{PsiClass, PsiModifier, PsiPackage}
import org.jetbrains.plugins.scala.editor.documentationProvider.{HtmlPsiUtils, _}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.highlighter.ScalaColorsSchemeUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition.DesugaredTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{AccessModifierRenderer, ModifiersRenderer}

private [documentationProvider] object WithHtmlPsiLink extends AccessQualifierRenderer {
  import ScalaModifier._

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

      modifierList
        .modifiersOrdered
        .filterNot(mod => mod == Private || mod == Protected || (mod == Implicit && isGivenElement))
        .foreach(mod => buffer.appendKeyword(modifiersToNames(mod)).append(' '))
    }
  }

  private val modifiersToNames: Map[ScalaModifier, String] = Map(
    Abstract    -> "abstract",
    Case        -> "case",
    Final       -> "final",
    Implicit    -> "implicit",
    Infix       -> "infix",
    Inline      -> "inline",
    Lazy        -> "lazy",
    Opaque      -> "opaque",
    Open        -> "open",
    Override    -> "override",
    Private     -> "private",
    Protected   -> "protected",
    Sealed      -> "sealed",
    Transparent -> "transparent"
  )

  override def renderQualifier(modifier: ScAccessModifier): String = {
    val res = for {
      id        <- modifier.idText
      reference <- Option(modifier.getReference)
      resolved  <- Option(reference.resolve())
      qualifier <- resolved match {
        case c: PsiClass => Some(c.qualifiedName)
        case p: PsiPackage => Some(p.getQualifiedName)
        case _ => None
      }
    } yield {
      val attributesKey = ScalaColorsSchemeUtils.textAttributesKey(resolved)
      HtmlPsiUtils.psiElementLinkWithCodeTag(qualifier, id, Some(attributesKey))
    }
    res.getOrElse("")
  }
}