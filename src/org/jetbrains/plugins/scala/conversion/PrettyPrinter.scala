package org.jetbrains.plugins.scala.conversion

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */

class PrettyPrinter {
  val printer = new StringBuilder()

  def append(value: String): PrettyPrinter = {
    printer.append(value)
    this
  }

  def delete(length: Int): PrettyPrinter = {
    printer.delete(printer.length - length, printer.length)
    this
  }

  def space(): PrettyPrinter = {
    printer.append(" ")
    this
  }

  def newLine(): PrettyPrinter = {
    printer.append("\n")
    this
  }

  def length = printer.length
  override def toString: String = {
    printer.toString()
  }
}
