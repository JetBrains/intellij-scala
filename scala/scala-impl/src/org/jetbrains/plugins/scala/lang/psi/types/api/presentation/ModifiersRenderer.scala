package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.ModifiersRenderer.modifiers

trait ModifiersRendererLike {
  def render(buffer: StringBuilder, owner: ScModifierListOwner): Unit

  final def render(elem: ScModifierListOwner):String = {
    val buffer = new StringBuilder
    render(buffer, elem)
    buffer.result()
  }
}

class ModifiersRenderer(
  accessModifierRenderer: AccessModifierRenderer
) extends ModifiersRendererLike {

  // TODO: maybe we should use modifiers order defined in `Code Style Settings | Scala | Arrangement`?
  override def render(buffer: StringBuilder, owner: ScModifierListOwner): Unit = {
    val modifierList = owner.getModifierList
    for (modifier <- modifierList.accessModifier) {
      val modifierText = accessModifierRenderer.render(modifier)
      buffer.append(modifierText).append(" ")
    }

    for (modifier <- modifiers if modifierList.hasModifierProperty(modifier))
      buffer.append(modifier).append(" ")
  }
}

object ModifiersRenderer {
  val modifiers: Array[String] = Array(
    "abstract", "override", "final", "sealed", "implicit", "lazy",
    "opaque", "inline", "transparent", "open", "infix", "case"
  )

  def SimpleText(
    textEscaper: TextEscaper = TextEscaper.Noop
  ): ModifiersRenderer =
    new ModifiersRenderer(new AccessModifierRenderer(new AccessQualifierRenderer.SimpleText(textEscaper)))
}