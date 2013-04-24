package org.jetbrains.sbt

import sbt._
import sbt.Keys._
import sbt.Load.BuildStructure
import xml.{PrettyPrinter, XML}
import java.io.FileWriter

/**
 * @author Pavel Fatin
 */
object Plugin extends (State => State) {
  def apply(state: State): State = {
    val log = state.log

    val structure = Extractor.extractStructure(state)

    val printer = new PrettyPrinter(180, 2)
    val text = printer.format(structure.toXML)

    Keys.artifactPath.in(Project.current(state)).get(Project.extract(state).structure.data).map { file =>
      log.info("Writing structure to " + file.getPath + "...")
      write(file, text)
      log.info("Done.")
    } getOrElse {
      log.info("Writing structure to console:")
      println(text)
      log.info("Done.")
    }

    state
  }

  private def write(file: File, text: String) {
    val writer = new FileWriter(file)
    try {
      writer.write(text)
      writer.flush()
    } finally {
      writer.close()
    }
  }
}
