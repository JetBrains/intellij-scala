package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer

trait ModifiersRendererLike {
  def render(modifierList: ScModifierList): String

  final def render(elem: ScModifierListOwner): String = {
    val list = elem.getModifierList
    if (list != null)
      render(list)
    else ""
  }
}

class ModifiersRenderer(
  accessModifierRenderer: AccessModifierRenderer
) extends ModifiersRendererLike {

  // TODO: maybe we should use modifiers order defined in `Code Style Settings | Scala | Arrangement`?
  override def render(modifierList: ScModifierList): String = {
    val buffer: StringBuilder = new StringBuilder

    for (modifier <- modifierList.accessModifier) {
      val modifierText = accessModifierRenderer.render(modifier)
      buffer.append(modifierText).append(" ")
    }

    val modifiers: Array[String] = Array("abstract", "override", "final", "sealed", "implicit", "lazy")

    for (modifier <- modifiers if modifierList.hasModifierProperty(modifier))
      buffer.append(modifier).append(" ")

    buffer.toString
  }
}


object ModifiersRenderer {

  val WithHtmlPsiLink: ModifiersRenderer =
    new ModifiersRenderer(new AccessModifierRenderer(AccessQualifierRenderer.WithHtmlPsiLink))

  def SimpleText(
    textEscaper: TextEscaper = TextEscaper.Noop
  ): ModifiersRenderer =
    new ModifiersRenderer(new AccessModifierRenderer(new AccessQualifierRenderer.SimpleText(textEscaper)))
}