package org.jetbrains.plugins.scala.conversion.visitors

import org.jetbrains.plugins.scala.conversion.PrettyPrinter
import org.jetbrains.plugins.scala.conversion.ast.IntermediateNode
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * Created by Kate Ustyuzhanina
  * on 11/24/15
  */
trait IntermediateTreeVisitor {
  def visit(node: IntermediateNode)

  val printer: PrettyPrinter

  def escapeKeyword(name: String): String = if (ScalaNamesUtil.isKeyword(name)) "`" + name + "`" else name

  def stringResult = printer.toString
}