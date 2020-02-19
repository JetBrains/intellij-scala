package org.jetbrains.plugins.scala
package internal
package bundle

import java.io.File
import java.util.Scanner
import java.util.regex.Pattern

import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent._

import scala.io.Source

object ScalaBundleSorting {
  case class ModuleInfo(rootPath: String, bundlePath: String, searcher: Searcher = new Searcher)

  val scalaModDir: String = TestUtils.findCommunityRoot() + "scala/"

  val bspModule: ModuleInfo = ModuleInfo(
    rootPath = TestUtils.findCommunityRoot() + "bsp/src/",
    bundlePath = TestUtils.findCommunityRoot() + "bsp/resources/messages/BspBundle.properties",
  )

  val codeInsightModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "codeInsight/src/",
    bundlePath = scalaModDir + "codeInsight/resources/messages/ScalaCodeInsightBundle.properties",
  )

  val conversionModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "conversion/src/",
    bundlePath = scalaModDir + "conversion/resources/messages/ScalaConversionBundle.properties",
  )

  val scalaImplDir: String = scalaModDir + "scala-impl/"
  val scalaImplModule: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir + "src/",
    bundlePath = scalaImplDir + "resources/org/jetbrains/plugins/scala/ScalaBundle.properties",
  )
  val scalaImplModuleErrMsg: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir + "src/",
    bundlePath = scalaImplDir + "resources/org/jetbrains/plugins/scala/editor/EditorBundle.properties",
  )

  val scalaImplModuleCodeInspection: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir + "src/",
    bundlePath = scalaImplDir + "resources/org/jetbrains/plugins/scala/codeInspection/InspectionBundle.properties",
  )

  val scalaImplModuleSbt: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir + "src/",
    bundlePath = scalaImplDir + "resources/org/jetbrains/sbt/SbtBundle.properties"
  )

  val uastModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "uast/src/",
    bundlePath = scalaModDir + "uast/resources/messages/ScalaUastBundle.properties",
  )

  val worksheetModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "worksheet/src/",
    bundlePath = scalaModDir + "worksheet/resources/messages/WorksheetBundle.properties",
  )

  val allModuleInfos: Seq[ModuleInfo] = Seq(
    bspModule,
    codeInsightModule,
    conversionModule,
    scalaImplModule, scalaImplModuleErrMsg, scalaImplModuleCodeInspection, scalaImplModuleSbt,
    uastModule,
    worksheetModule
  )




  def main(args: Array[String]): Unit = sortAll(allModuleInfos)

  def sortAll(moduleInfos: Seq[ModuleInfo]): Unit = for (info <- moduleInfos) {
    val ModuleInfo(rootPath, bundlePath, searcher) = info
    println(s"Find keys in $rootPath")
    val findings = findKeysInModule(rootPath, searcher)
    val keyToFinding = findings.groupBy(_.key)

    println(s"Read bundle $bundlePath")
    val I18nBundleContent(entries) = read(bundlePath)
    val keyToAmountOfEntries = entries.groupBy(_.key).mapValues(_.size)

    def isEntryInInvalidPath(entry: Entry): Boolean =
      !keyToFinding.getOrElse(entry.key, Nil).exists(_.relativeFilepath == entry.path)

    println(s"Process unused and invalid entries...")
    var changed = 0
    val entriesWithPath =
      entries.map {
        case entry if entry.isUnused || isEntryInInvalidPath(entry) =>
          val newPath = keyToFinding
            .get(entry.key)
            .map(_.maxBy(f => keyToAmountOfEntries(f.key)))
            .fold(unusedPath)(_.relativeFilepath)
          if (entry.path != newPath)
            changed += 1
          entry.copy(path = newPath)
        case entry =>
          entry
      }
    println(s"$changed entries changed...")
    println(s"Write bundle $bundlePath")
    I18nBundleContent(entriesWithPath)
        .sorted
        .writeTo(bundlePath)
    println("Done.")
    println()
  }

  class Searcher {
    val pattern: Pattern =
      """(?:(?:message|ErrMsg)\s*\(\s*|key=)"(.+?)"""".r.pattern

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
      val absoluteFilepath = file.toString.replace('\\', '/')
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
