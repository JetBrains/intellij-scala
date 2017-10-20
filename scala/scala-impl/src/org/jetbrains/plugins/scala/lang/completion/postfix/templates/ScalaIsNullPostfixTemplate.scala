package org.jetbrains.plugins.scala.lang.completion.postfix.templates

/**
 * @author Roman.Shein
 * @since 14.09.2015.
 */
class ScalaIsNullPostfixTemplate extends ScalaNullPostfixTemplate("null", "if (expr == null) {}") {

  override def getTail = "== null"
}
