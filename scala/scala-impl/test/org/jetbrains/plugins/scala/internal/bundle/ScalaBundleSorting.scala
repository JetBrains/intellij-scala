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

  trait ModuleInfoLike {
    def rootPath: String
    def searcher: Searcher

    def srcPath: String = rootPath + "src/"
    def resourcesPath: String = rootPath + "resources/"
    def messagesPath: String = resourcesPath + "messages/"
  }

  case class ModuleInfo(
    override val rootPath: String,
    override val searcher: Searcher = new Searcher
  ) extends ModuleInfoLike

  /**
   * @param rootPath                   path to the root of the module
   * @param bundleMessagesRelativePath path of bundle relative to `messages` folder
   * @param extraUsageModules          list of modules which might reference keys from the bundle<br>
   *                                   For example keys might be defined in play/resources/messages/ScalaPlay2Bundle.properties<br>
   *                                   but it has to be used from resources/META-INF/ultimateScala.xml<br>
   */
  case class ModuleWithBundleInfo(
    override val rootPath: String,
    bundleMessagesRelativePath: String,
    extraUsageModules: Seq[ModuleInfo] = Nil,
    override val searcher: Searcher = new Searcher
  ) extends ModuleInfoLike {
    def bundleAbsolutePath: String = messagesPath + bundleMessagesRelativePath
  }

  val communityDir: String = TestUtils.findCommunityRoot
  val scalaModDir: String = communityDir + "scala/"
  val sbtModDir: String = communityDir + "sbt/"
  val scalaImplDir: String = scalaModDir + "scala-impl/"

  val allModuleInfos: Seq[ModuleWithBundleInfo] = Seq(
    ModuleWithBundleInfo(
      rootPath = communityDir + "bsp/",
      bundleMessagesRelativePath = "ScalaBspBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "codeInsight/",
      bundleMessagesRelativePath = "ScalaCodeInsightBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "compile-server/",
      bundleMessagesRelativePath = "ScalaCompileServerBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "compiler-integration/",
      bundleMessagesRelativePath = "CompilerIntegrationBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "compiler-jps/",
      bundleMessagesRelativePath = "ScalaJpsBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "compiler-shared/",
      bundleMessagesRelativePath = "CompilerSharedBuildBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "compiler-shared/",
      bundleMessagesRelativePath = "ScalaCompileServerSharedBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "conversion/",
      bundleMessagesRelativePath = "ScalaConversionBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "debugger/",
      bundleMessagesRelativePath = "DebuggerBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "integration/devKit/",
      bundleMessagesRelativePath = "ScalaDevkitBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "integration/intellilang/",
      bundleMessagesRelativePath = "ScalaIntellilangBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "integration/packagesearch/",
      bundleMessagesRelativePath = "PackageSearchSbtBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "integration/properties/",
      bundleMessagesRelativePath = "ScalaI18nBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = sbtModDir + "sbt-api/",
      bundleMessagesRelativePath = "SbtApiBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = sbtModDir + "sbt-impl/",
      bundleMessagesRelativePath = "SbtBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaEditorBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaInspectionBundle.properties",
      extraUsageModules = Seq(
        ModuleInfo(scalaModDir + "integration/properties/")
      )
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaMetaBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "test-integration/testing-support/",
      bundleMessagesRelativePath = "TestingSupportBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir + "worksheet/",
      bundleMessagesRelativePath = "ScalaWorksheetBundle.properties",
    ),
  )

  def main(args: Array[String]): Unit = sortAll(allModuleInfos)

  def sortAll(moduleInfos: Seq[ModuleWithBundleInfo]): Unit = for (moduleInfo <- moduleInfos) {
    val keyToFindings: Map[String, List[Finding]] =
      findKeyUsages(moduleInfo)

    val bundlePath = moduleInfo.bundleAbsolutePath
    println(s"Read bundle $bundlePath")
    val I18nBundleContent(entries) = read(bundlePath)
    val keyToAmountOfEntries = entries.groupBy(_.key).view.mapValues(_.size)

    def isEntryInInvalidPath(entry: Entry): Boolean =
      !keyToFindings.getOrElse(entry.key, Nil).exists(_.relativeFilepath == entry.path)

    println(s"Process unused and invalid entries...")
    var changed = 0
    val entriesWithPath =
      entries.map {
        case entry if entry.isUnused || isEntryInInvalidPath(entry) =>
          val newPath = keyToFindings
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

  def findKeyUsages(moduleInfo: ModuleWithBundleInfo): Map[String, List[Finding]] = {
    println(s"Find keys in ${moduleInfo.rootPath}")
    val findings = findKeysInModule(moduleInfo)

    val findingsExtra = moduleInfo.extraUsageModules.flatMap { extraModuleInfo =>
      println(s"Find keys in extra module ${extraModuleInfo.rootPath}")
      findKeysInModule(extraModuleInfo)
    }

    val findingsAll = findings ++ findingsExtra
    findingsAll.groupBy(_.key)
  }

  class Searcher {
    val pattern: Pattern =
      """(?:(?:(?:message|ErrMsg|nls)\s*\(\s*|groupPathKey=|groupKey=|key=)"(.+?)")|(?:<categoryKey>(.+?)</categoryKey>)""".r.pattern

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

  def findKeysInModule(module: ModuleInfoLike): List[Finding] =
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
