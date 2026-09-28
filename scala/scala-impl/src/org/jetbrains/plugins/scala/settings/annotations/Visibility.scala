package org.jetbrains.plugins.scala.settings.annotations

sealed trait Visibility

object Visibility {
  object Private extends Visibility
  object Protected extends Visibility
  object Default extends Visibility

  def apply(s: String): Visibility =
    if (s == null) Visibility.Default
    else if (s.startsWith("private")) Visibility.Private
    else if (s.startsWith("protected")) Visibility.Protected
    else Visibility.Default
}
