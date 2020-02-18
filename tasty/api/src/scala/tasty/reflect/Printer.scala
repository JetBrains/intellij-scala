package scala.tasty.reflect

import scala.tasty.Reflection

trait Printer[R <: Reflection with Singleton] {
  val tasty: R 
  def showTree(tree: tasty.Tree)(implicit ctx: tasty.Context): String 
  def showTypeOrBounds(tpe: tasty.TypeOrBounds)(implicit ctx: tasty.Context): String 
  def showConstant(const: tasty.Constant)(implicit ctx: tasty.Context): String 
  def showSymbol(symbol: tasty.Symbol)(implicit ctx: tasty.Context): String 
  def showFlags(flags: tasty.Flags)(implicit ctx: tasty.Context): String 
} 
