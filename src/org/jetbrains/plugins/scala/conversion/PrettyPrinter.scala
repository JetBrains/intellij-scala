package org.jetbrains.plugins.scala.conversion

import org.jetbrains.plugins.scala.conversion.ast.IntermediateNode

/**
  * Created by user
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

  def append(seq: Seq[IntermediateNode], separator: String): PrettyPrinter = {
    if (seq != null && seq.nonEmpty) {
      val it = seq.iterator
      while (it.hasNext) {
        it.next().print(this)
        if (it.hasNext) printer.append(separator)
      }
    }
    this
  }

  def append(seq: Seq[IntermediateNode], separator: String, before: String, after: String, needAppend: Boolean): PrettyPrinter = {
    if (needAppend) append(before)
    append(seq, separator)
    if (needAppend) append(after)
    this
  }

  def append(seq: Seq[IntermediateNode], separator: String, before: String, after: String): PrettyPrinter = {
    append(seq, separator, before, after, needAppend = true)
  }

  def length = printer.length
  override def toString: String = {
    printer.toString()
  }
}
