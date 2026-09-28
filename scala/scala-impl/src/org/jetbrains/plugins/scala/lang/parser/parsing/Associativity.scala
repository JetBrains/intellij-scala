package org.jetbrains.plugins.scala.lang.parser.parsing

sealed abstract class Associativity
object Associativity {
  sealed abstract class LeftOrRight extends Associativity
  case object Left extends LeftOrRight
  case object Right extends LeftOrRight
  case object NoAssociativity extends Associativity
}