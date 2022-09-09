package org.jetbrains.plugins.scala.internal.bundle

import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent._

import java.io.File
import java.util.Scanner
import java.util.regex.Pattern
import scala.io.Source

//noinspection ScalaUnusedSymbol
object ScalaBundleSorting {

  case class ModuleInfo(rootPath: String, bundleMessagesRelativePath: String, searcher: Searcher = new Searcher) {
    def srcPath: String = rootPath + "src/"
    def resourcesPath: String = rootPath + "resources/"
    def messagesPath: String = resourcesPath + "messages/"
    def bundleAbsolutePath: String = messagesPath + bundleMessagesRelativePath
  }

  val scalaModDir: String = TestUtils.findCommunityRoot() + "scala/"
  val scalaImplDir: String = scalaModDir + "scala-impl/"

  val allModuleInfos: Seq[ModuleInfo] = Seq(
    ModuleInfo(
      rootPath = TestUtils.findCommunityRoot() + "bsp/",
      bundleMessagesRelativePath = "ScalaBspBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "codeInsight/",
      bundleMessagesRelativePath = "ScalaCodeInsightBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "conversion/",
      bundleMessagesRelativePath = "ScalaConversionBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "integration/devKit/",
      bundleMessagesRelativePath = "ScalaDevkitBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "compiler-jps/",
      bundleMessagesRelativePath = "ScalaJpsBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "compiler-shared/",
      bundleMessagesRelativePath = "ScalaCompileServerSharedBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "macros/",
      bundleMessagesRelativePath = "ScalaMacrosBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaEditorBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaInspectionBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaSbtBundle.properties"
    ),
    ModuleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaMetaBundle.properties"
    ),
    ModuleInfo(
      rootPath = scalaModDir + "debugger/",
      bundleMessagesRelativePath = "DebuggerBundle.properties"
    ),
    ModuleInfo(
      rootPath = scalaModDir + "testing-support/",
      bundleMessagesRelativePath = "TestingSupportBundle.properties"
    ),
    ModuleInfo(
      rootPath = scalaModDir + "uast/",
      bundleMessagesRelativePath = "ScalaUastBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "worksheet/",
      bundleMessagesRelativePath = "ScalaWorksheetBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "integration/intellilang/",
      bundleMessagesRelativePath = "ScalaIntellilangBundle.properties",
    ),
    ModuleInfo(
      rootPath = scalaModDir + "integration/packagesearch/",
      bundleMessagesRelativePath = "PackageSearchSbtBundle.properties",
    ),
  )

  def main(args: Array[String]): Unit = sortAll(allModuleInfos)

  def sortAll(moduleInfos: Seq[ModuleInfo]): Unit = for (info <- moduleInfos) {
    val ModuleInfo(rootPath, _, _) = info
    val bundlePath = info.bundleAbsolutePath
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
      """(?:(?:(?:message|ErrMsg|nls)\s*\(\s*|groupKey=|key=)"(.+?)")|(?:<categoryKey>(.+?)</categoryKey>)""".r.pattern

    def search(file: File): Seq[String] = {
      val result = Seq.newBuilder[String]
      val scanner = new Scanner(Source.fromFile(file).bufferedReader())
      try {
        while (scanner.findWithinHorizon(pattern, 0) ne null) {
          val m = scanner.`match`()

          val g1 = m.group(1)
          val g =
            if (g1 != null) g1 // from message("blub")
            else m.group(2) // from <categoryKey>blub</categoryKey>
          assert(g != null)
          result += g
        }
      } finally {
        scanner.close()
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
