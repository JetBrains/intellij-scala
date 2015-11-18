package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
case class TypeParameterConstruction(name: String, typez: Seq[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append(escapeKeyword(name))
    if (typez.nonEmpty) {
      printer.append(" <: ")
      printer.append(typez, " with ")
    }
    printer
  }
}
