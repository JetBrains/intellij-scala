package org.jetbrains.sbt

import sbt._
import sbt.Keys._
import sbt.Load.BuildStructure
import xml.{PrettyPrinter, XML}

/**
 * @author Pavel Fatin
 */
object Plugin extends (State => State) {
  def apply(state: State): State = {
    val structure = Extractor.extractStructure(state)

    val printer = new PrettyPrinter(180, 2)
    println(printer.format(structure.toXML))

    state
  }
}
