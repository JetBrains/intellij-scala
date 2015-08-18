package org.jetbrains.sbt
package project.structure

import java.io.{BufferedInputStream, Closeable, File}
import java.util.jar.{JarEntry, JarFile}

/**
 * @author Nikolay Obedin
 * @since 8/18/15.
 */
object SbtBootPropertiesReader {

  final case class Property(name: String, value: String)

  final case class Section(name: String, properties: Seq[Property])

  def apply(file: File, sectionName: String): Seq[Property] =
    apply(file).find(_.name == sectionName).fold(Seq.empty[Property])(_.properties)

  def apply(file: File): Seq[Section] = {
    val jar = new JarFile(file)
    try {
      Option(jar.getEntry("sbt/sbt.boot.properties")).fold(Seq.empty[Section]) { entry =>
        using(new BufferedInputStream(jar.getInputStream(entry))) { input =>
          val lines = scala.io.Source.fromInputStream(input).getLines()
          readLines(lines)
        }
      }
    }
    finally {
      if (jar.isInstanceOf[Closeable]) {
        jar.close()
      }
    }
  }

  type ReaderState = (Seq[Section], Option[String], Seq[Property])

  private val emptyReaderState: ReaderState = (Seq.empty, None, Seq.empty)

  private def readLines(lines: Iterator[String]): Seq[Section] = {
    val (prevSections, lastSection, lastProperties) =
      lines.map(_.trim).foldLeft[ReaderState](emptyReaderState)((acc, line) => readLine(line, acc))
    appendSection(prevSections, lastSection, lastProperties)
  }

  private def readLine(line: String, state: ReaderState): ReaderState = state match {
    case (prevSections, lastSection, lastProperties) =>
      if (line.startsWith("#")) {
        state
      } else if (line.startsWith("[")) {
        val newSections = appendSection(prevSections, lastSection, lastProperties)
        val braceEnd = line.indexOf(']')
        val section = (braceEnd != 1).option(line.substring(1, braceEnd).trim)
        (newSections, section, Seq.empty)
      } else {
        line.split(":", 2) match {
          case Array(name, value) => (prevSections, lastSection, lastProperties :+ Property(name.trim, value.trim))
          case _ => state
        }
      }
  }

  private def appendSection(sections: Seq[Section], sectionName: Option[String], properties: Seq[Property]): Seq[Section] =
    sections ++ sectionName.map(name => Section(name, properties)).toSeq
}


