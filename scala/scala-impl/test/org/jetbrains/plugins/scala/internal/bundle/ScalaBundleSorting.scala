package org.jetbrains.plugins.scala
package internal
package bundle

import java.io.File
import java.util.Scanner
import java.util.regex.Pattern

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.TestUtils

import scala.io.Source

object ScalaBundleSorting {
  val noCategoryPath = "<no-category>"
  val unusedCategoryPath = "<unused>"
  case class ModuleInfo(rootPath: String, bundlePath: String, searcher: Searcher)

  val scalaImplDir: String = TestUtils.findCommunityRoot() + "scala/scala-impl/"
  val scalaImplModule: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir + "src/",
    bundlePath = scalaImplDir + "resources/org/jetbrains/plugins/scala/ScalaBundle.properties",
    searcher = new Searcher
  )

  val allModuleInfos: Seq[ModuleInfo] = Seq(
    scalaImplModule,
  )

  def main(args: Array[String]): Unit = for (info <- allModuleInfos) {
    val ModuleInfo(rootPath, bundlePath, searcher) = info
    println(s"Process $rootPath ($bundlePath)")
    val findings = findKeysInModule(rootPath, searcher)
    val keyToFinding = findings.groupBy(_.key)
    val entries = readBundle(bundlePath)
    val keyToAmountOfEntries = entries.groupBy(_.key).mapValues(_.size)

    val entriesWithPath =
      entries.map {
        case entry@Entry(key, _, `noCategoryPath`, _) =>
          val newCat = keyToFinding
            .get(key)
            .map(_.maxBy(f => keyToAmountOfEntries(f.key)))
            .fold(unusedCategoryPath)(_.relativeFilepath)
          entry.copy(path = newCat)
        case entry =>
          entry
      }.sortBy(_.path)


    import java.io._
    val pw = new PrintWriter(new File(bundlePath))

    try {
      import pw._
      var path: String = null
      for (entry <- entriesWithPath) {
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
    println("Done.")
  }

  class Searcher {
    val pattern: Pattern =
      """(?:message|ErrMsg)\s*\(\s*"(.+?)"""".r.pattern

    def search(file: File): Seq[String] = {
      val result = Seq.newBuilder[String]
      val reader = Source.fromFile(file).bufferedReader()
      val scanner = new Scanner(reader)
      while (scanner.findWithinHorizon(pattern, 0) ne null) {
        val m = scanner.`match`()
        result += m.group(1)
      }

      result.result()
    }
  }

  case class Finding(relativeFilepath: String, key: String)(val absoluteFilepath: String)
  case class Entry(key: String, text: String, path: String, comments: String)

  def findKeysInModule(path: String, searcher: Searcher): List[Finding] =
    for (file <- allFilesIn(path).toList.sorted; key <- searcher.search(file)) yield {
      val absoluteFilepath = file.toString
      val relativeFilepath = absoluteFilepath.substring(path.length)
      Finding(relativeFilepath, key)(absoluteFilepath)
    }

  def readBundle(bundlepath: String): Seq[Entry] = {
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

    result.result()
  }

  object NewCategoryLines {
    val prefix = "### "
    def unapply(line: String): Option[String] = {
      val isNewCategory = line.startsWith(prefix) &&
        (line.contains('.') && line.contains('/') || line.contains(unusedCategoryPath))
      isNewCategory.option(line.substring(prefix.length))
    }
  }

  def allFilesIn(path: String): Iterator[File] =
    allFilesIn(new File(path))

  def allFilesIn(path: File): Iterator[File] = {
    if (!path.exists) Iterator()
    else if (!path.isDirectory) Iterator(path)
    else path.listFiles.sorted.iterator.flatMap(allFilesIn)
  }
}
