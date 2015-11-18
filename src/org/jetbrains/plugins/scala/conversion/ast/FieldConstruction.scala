package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
//TODO setter&getter
case class FieldConstruction(modifiers: IntermediateNode, name: String,
                             ftype: IntermediateNode, isVar: Boolean,
                             initalaizer: Option[IntermediateNode]) extends IntermediateNode with TypedElement {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    modifiers.print(printer)

    if (isVar) {
      printer.append("var")
    } else {
      printer.append("val")
    }
    printer.space()
    printer.append(escapeKeyword(name))
    printer.append(": ")
    ftype.print(printer)
    printer.append(" = ")
    if (initalaizer.isDefined) {
      initalaizer.get.print(printer)
    } else {
      printer.append(ftype match {
        case tc: TypeConstruction => tc.getDefaultTypeValue
        case _ => ""
      })
    }
  }

  override def getType: TypeConstruction = ftype.asInstanceOf[TypedElement].getType
}
