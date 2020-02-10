package org.jetbrains.plugins.scala
package internal
package bundle

import java.io.File
import java.util.Scanner
import java.util.regex.Pattern

import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.internal.SortedScalaStringBundle
import org.jetbrains.plugins.scala.util.internal.SortedScalaStringBundle._

import scala.io.Source

object ScalaBundleSorting {
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
    val SortedScalaStringBundle(entries) = readBundle(bundlePath)
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
      }

    SortedScalaStringBundle(entriesWithPath)
        .sorted
        .writeTo(bundlePath)
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

  def findKeysInModule(path: String, searcher: Searcher): List[Finding] =
    for (file <- allFilesIn(path).toList.sorted; key <- searcher.search(file)) yield {
      val absoluteFilepath = file.toString
      val relativeFilepath = absoluteFilepath.substring(path.length)
      Finding(relativeFilepath, key)(absoluteFilepath)
    }

  def allFilesIn(path: String): Iterator[File] =
    allFilesIn(new File(path))

  def allFilesIn(path: File): Iterator[File] = {
    if (!path.exists) Iterator()
    else if (!path.isDirectory) Iterator(path)
    else path.listFiles.sorted.iterator.flatMap(allFilesIn)
  }
}
