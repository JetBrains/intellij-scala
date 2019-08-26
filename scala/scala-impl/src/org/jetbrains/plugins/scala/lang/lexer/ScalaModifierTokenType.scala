package org.jetbrains.plugins.scala
package lang
package lexer

import com.intellij.psi.tree.IElementType

final class ScalaModifierTokenType private(val modifier: ScalaModifier) extends ScalaTokenType(modifier.text())

object ScalaModifierTokenType {

  val Inline = ScalaModifierTokenType(ScalaModifier.Inline);
  val Opaque = ScalaModifierTokenType(ScalaModifier.Opaque)

  def apply(modifier: ScalaModifier): ScalaModifierTokenType = cache.get(modifier) match {
    case null =>
      val result = new ScalaModifierTokenType(modifier)
      cache.put(modifier, result)
      result
    case cached => cached
  }

  def unapply(elementType: IElementType): Option[ScalaModifier] = elementType match {
    case tokenType: ScalaModifierTokenType => Some(tokenType.modifier)
    case _ => None
  }

  private val cache = new java.util.EnumMap[ScalaModifier, ScalaModifierTokenType](classOf[ScalaModifier])
}