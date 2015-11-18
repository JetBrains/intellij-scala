package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

/**
  * Created by Kate Ustyuzhanina
  * on 10/28/15
  */
case class TypeParameters(data: Seq[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(data, ", ", "[", "]", data.nonEmpty)
  }
}
