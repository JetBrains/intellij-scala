package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

case class PhaseWithTreeText(
  phaseName: String,
  phaseText: String,
  phaseKind: PhaseWithTreeText.PhaseKind = PhaseWithTreeText.PhaseKind.Regular
)

object PhaseWithTreeText {
  sealed trait PhaseKind
  object PhaseKind {
    case object Regular extends PhaseKind
    case object TastyOutput extends PhaseKind
    case object UncapturedOutput extends PhaseKind
  }
}
