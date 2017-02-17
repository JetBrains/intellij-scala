package org.jetbrains.plugins.scala.conversion.visitors

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.conversion.PrettyPrinter
import org.jetbrains.plugins.scala.conversion.ast.IntermediateNode
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.mutable

/**
  * Created by Kate Ustyuzhanina
  * on 11/24/15
  */
trait IntermediateTreeVisitor {
  def visit(node: IntermediateNode)

  val printer: PrettyPrinter

  def escapeKeyword(name: String): String = ScalaNamesUtil.escapeKeyword(name)

  def stringResult: String = StringUtil.convertLineSeparators(printer.toString)

  val rangedElementsMap = new mutable.HashMap[IntermediateNode, TextRange]
}