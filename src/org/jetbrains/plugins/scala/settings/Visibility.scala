package org.jetbrains.plugins.scala.settings

/**
  * @author Pavel Fatin
  */
sealed trait Visibility

object Visibility {
  final object Private extends Visibility
  final object Protected extends Visibility
  final object Default extends Visibility

  def apply(s: String): Visibility =
    if (s.startsWith("private")) Visibility.Private
    else if (s.startsWith("protected")) Visibility.Protected
    else Visibility.Default
}
