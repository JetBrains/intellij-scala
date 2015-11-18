package org.jetbrains.plugins.scala.conversion.ast

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.conversion.PrettyPrinter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * Created by Kate Ustyuzhanina
  * on 10/21/15
  */

abstract class IntermediateNode {
  def print(printer: PrettyPrinter): PrettyPrinter

  def escapeKeyword(name: String): String = if (ScalaNamesUtil.isKeyword(name)) "`" + name + "`" else name

  def getRange: TextRange = new TextRange(0, 0)
}


case class EmptyConstruction() extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = printer.append("")
}

trait TypedElement {
  def getType: TypeConstruction
}