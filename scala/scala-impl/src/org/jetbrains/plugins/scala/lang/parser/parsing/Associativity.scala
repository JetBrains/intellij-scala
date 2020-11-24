package org.jetbrains.plugins.scala.lang.parser.parsing

sealed abstract class Associativity
object Associativity {
  sealed abstract class LeftOrRight extends Associativity
  final case object Left extends LeftOrRight
  final case object Right extends LeftOrRight
  final case object NoAssociativity extends Associativity
}