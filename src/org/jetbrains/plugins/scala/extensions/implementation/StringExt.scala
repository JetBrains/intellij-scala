package org.jetbrains.plugins.scala.extensions.implementation

/**
 * Pavel Fatin
 */

class StringExt(s: String) {
  def startsWith(c: Char) = !s.isEmpty && s.charAt(0) == c

  def endsWith(c: Char) = !s.isEmpty && s.charAt(s.length - 1) == c

  def parenthesisedIf(condition: Boolean) = if (condition) "(" + s + ")" else s
}
