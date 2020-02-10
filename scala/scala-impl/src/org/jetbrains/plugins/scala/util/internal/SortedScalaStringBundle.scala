package org.jetbrains.plugins.scala
package util
package internal

import java.io._

import org.jetbrains.plugins.scala.util.internal.SortedScalaStringBundle._

import scala.io.Source
import extensions._

case class SortedScalaStringBundle(entries: Seq[Entry]) {
  def sorted: SortedScalaStringBundle =
    copy(entries = entries.sortBy(_.path))

  def writeTo(bundlePath: String): Unit = {
    val pw = new PrintWriter(new File(bundlePath))

    try {
      import pw._
      var path: String = null
      for (entry <- entries) {
        if (path != entry.path) {
          if (path != null) {
            println()
          }
          path = entry.path
          println("### " + path)
        }
        print(entry.comments)
        println(entry.key + "=" + entry.text)
      }
    } finally pw.close()
  }
}

object SortedScalaStringBundle {
  val noCategoryPath = "<no-category>"
  val unusedCategoryPath = "<unused>"

  case class Entry(key: String, text: String, path: String, comments: String)

  def readBundle(bundlepath: String): SortedScalaStringBundle = {
    val lines = {
      val source = Source.fromFile(bundlepath)
      try source.getLines().toArray
      finally source.close()
    }

    var comments = ""
    var path = noCategoryPath
    val result = Seq.newBuilder[Entry]

    var i = 0
    while (lines.indices contains i) {
      val line = lines(i)
      line match {
        case NewCategoryLines(cat) =>
          if (comments != "")
            throw new Exception(s"Comment might get lost: '$comments'")
          path = cat

        case _ if line.startsWith("#") =>
          comments += line + "\n"

        case _ if line.contains("=") =>
          var (key, value) = line.split("=").toSeq match { case key +: rest => key -> rest.mkString("=")}
          while (value.last == '\\') {
            i += 1
            value = value + "\n" + lines(i)
          }
          result += Entry(key, value, path, comments)
          comments = ""
        case _ =>
      }
      i += 1
    }

    new SortedScalaStringBundle(result.result())
  }


  private object NewCategoryLines {
    val prefix = "### "
    def unapply(line: String): Option[String] = {
      val isNewCategory = line.startsWith(prefix) &&
        (line.contains('.') && line.contains('/') || line.contains(unusedCategoryPath))
      isNewCategory.option(line.substring(prefix.length))
    }
  }
}