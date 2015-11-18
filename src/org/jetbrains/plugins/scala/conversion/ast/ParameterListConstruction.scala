package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

/**
  * Created by Kate Ustyuzhanina
  * on 10/26/15
  */
case class ParameterListConstruction(list: Seq[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(list, ", ", "(", ")", list.nonEmpty)
  }
}
