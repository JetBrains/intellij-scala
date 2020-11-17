package scala.tasty.reflect

import scala.tasty.Reflection

trait Printer[R <: Reflection ] {
  val reflect: R
  import reflect._
  def showTree(tree: Tree): String
  def showType(tpe: TypeRepr): String
  def showConstant(const: Constant): String
  def showSymbol(symbol: Symbol): String
  def showFlags(flags: Flags): String
}
