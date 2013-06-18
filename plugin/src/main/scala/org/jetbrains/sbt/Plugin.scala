package org.jetbrains.sbt

import sbt._
import xml.PrettyPrinter
import java.io.FileWriter
import sbt.File

/**
 * @author Pavel Fatin
 */
private object Plugin {
  def read(state: State, download: Boolean) {
    val log = state.log

    log.info("Reading structure from " + System.getProperty("user.dir"))

    val structure = Extractor.extractStructure(state, download)

    val text = {
      val printer = new PrettyPrinter(180, 2)
      val home = new File(System.getProperty("user.home"))
      printer.format(structure.toXML(home))
    }

    Keys.artifactPath.in(Project.current(state)).get(Project.extract(state).structure.data).map { file =>
      log.info("Writing structure to " + file.getPath + "...")
      write(file, text)
      log.info("Done.")
    } getOrElse {
      log.info("Writing structure to console:")
      println(text)
      log.info("Done.")
    }
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

object ReadProject extends (State => State) {
  def apply(state: State) = const(state)(Plugin.read(state, download = false))
}

object ReadProjectAndRepository extends (State => State) {
  def apply(state: State) = const(state)(Plugin.read(state, download = true))
}
