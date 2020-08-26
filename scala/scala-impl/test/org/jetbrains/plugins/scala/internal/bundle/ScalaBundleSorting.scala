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
  case class ModuleInfo(rootPath: String, bundlePath: String, searcher: Searcher = new Searcher) {
    def srcPath: String = rootPath + "src/"
    def resourcesPath: String = rootPath + "resources/"
  }

  val scalaModDir: String = TestUtils.findCommunityRoot() + "scala/"

  val bspModule: ModuleInfo = ModuleInfo(
    rootPath = TestUtils.findCommunityRoot() + "bsp/",
    bundlePath = TestUtils.findCommunityRoot() + "bsp/resources/messages/ScalaBspBundle.properties",
  )

  val codeInsightModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "codeInsight/",
    bundlePath = scalaModDir + "codeInsight/resources/messages/ScalaCodeInsightBundle.properties",
  )

  val conversionModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "conversion/",
    bundlePath = scalaModDir + "conversion/resources/messages/ScalaConversionBundle.properties",
  )

  val devkitModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "integration/devKit/",
    bundlePath = scalaModDir + "integration/devKit/resources/messages/ScalaDevkitBundle.properties",
  )

  val jpsModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "compiler-jps/",
    bundlePath = scalaModDir + "compiler-jps/resources/messages/ScalaJpsBundle.properties",
  )

  val macrosModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "macros/",
    bundlePath = scalaModDir + "macros/resources/messages/ScalaMacrosBundle.properties",
  )

  val scalaImplDir: String = scalaModDir + "scala-impl/"
  val scalaImplModule: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir,
    bundlePath = scalaImplDir + "resources/messages/ScalaBundle.properties",
  )
  val scalaImplModuleErrMsg: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir,
    bundlePath = scalaImplDir + "resources/messages/ScalaEditorBundle.properties",
  )

  val scalaImplModuleCodeInspection: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir,
    bundlePath = scalaImplDir + "resources/messages/ScalaInspectionBundle.properties",
  )

  val scalaImplModuleSbt: ModuleInfo = ModuleInfo(
    rootPath = scalaImplDir,
    bundlePath = scalaImplDir + "resources/messages/ScalaSbtBundle.properties"
  )

  val uastModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "uast/",
    bundlePath = scalaModDir + "uast/resources/messages/ScalaUastBundle.properties",
  )

  val worksheetModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "worksheet/",
    bundlePath = scalaModDir + "worksheet/resources/messages/ScalaWorksheetBundle.properties",
  )

  val intellilangModule: ModuleInfo = ModuleInfo(
    rootPath = scalaModDir + "integration/intellilang/",
    bundlePath = scalaModDir + "integration/intellilang/resources/messages/ScalaIntellilangBundle.properties",
  )

  val allModuleInfos: Seq[ModuleInfo] = Seq(
    bspModule,
    codeInsightModule,
    conversionModule,
    scalaImplModule, scalaImplModuleErrMsg, scalaImplModuleCodeInspection, scalaImplModuleSbt,
    uastModule,
    worksheetModule,
    intellilangModule
  )




  def main(args: Array[String]): Unit = sortAll(allModuleInfos)

  def sortAll(moduleInfos: Seq[ModuleInfo]): Unit = for (info <- moduleInfos) {
    val ModuleInfo(rootPath, bundlePath, _) = info
    println(s"Find keys in $rootPath")
    val findings = findKeysInModule(info)
    val keyToFinding = findings.groupBy(_.key)

    println(s"Read bundle $bundlePath")
    val I18nBundleContent(entries) = read(bundlePath)
    val keyToAmountOfEntries = entries.groupBy(_.key).view.mapValues(_.size)

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
      """(?:(?:message|ErrMsg|nls)\s*\(\s*|groupKey=|key=)"(.+?)"""".r.pattern

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

  def findKeysInModule(module: ModuleInfo): List[Finding] =
    findKeysInDirectory(module.srcPath, module.searcher) ++
      findKeysInDirectory(module.resourcesPath, module.searcher)

  def findKeysInDirectory(path: String, searcher: Searcher): List[Finding] =
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
