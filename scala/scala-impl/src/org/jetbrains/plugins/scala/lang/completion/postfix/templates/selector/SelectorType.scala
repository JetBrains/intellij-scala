package org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector

/**
 * @author Roman.Shein
 * @since 14.09.2015.
 */
object SelectorType extends Enumeration {
  type SelectorType = Value
  val First, Topmost, All = Value
}