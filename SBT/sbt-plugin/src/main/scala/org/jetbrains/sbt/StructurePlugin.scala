package org.jetbrains.sbt

import scala.xml.PrettyPrinter
import java.io.FileWriter
import sbt._
import Keys._

/**
 * @author Pavel Fatin
 */
object StructurePlugin extends Plugin {
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

  override lazy val settings: Seq[Setting[_]] = Seq(commands ++= Seq(readProjectCommand, readProjectAndRepositoryCommand))

  lazy val readProjectCommand = Command.command("read-project")((s: State) => ReadProject(s))

  lazy val readProjectAndRepositoryCommand = Command.command("read-project-and-repository")((s: State) => ReadProjectAndRepository(s))
}

object ReadProject extends (State => State) {
  def apply(state: State) = Function.const(state)(StructurePlugin.read(state, download = false))
}

object ReadProjectAndRepository extends (State => State) {
  def apply(state: State) = Function.const(state)(StructurePlugin.read(state, download = true))
}
