package org.jetbrains.plugins.scala.lang.lexer

case class ScalaModifierTokenType private (modifier: ScalaModifier) extends ScalaKeywordTokenType(modifier.text())

object ScalaModifierTokenType {

  def of(mod: ScalaModifier): ScalaModifierTokenType = ScalaModifierTokenType(mod)

  def apply(mod: ScalaModifier): ScalaModifierTokenType = {
    if (cache.get(mod) == null) {
      cache.put(mod, new ScalaModifierTokenType(mod))
    }
    cache.get(mod)
  }

  private val cache = new java.util.EnumMap[ScalaModifier, ScalaModifierTokenType](classOf[ScalaModifier])
}