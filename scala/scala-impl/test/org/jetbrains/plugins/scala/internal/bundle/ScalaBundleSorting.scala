package org.jetbrains.plugins.scala.internal.bundle

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent._

import java.io.File
import java.nio.file.Path
import java.util.Scanner
import java.util.regex.Pattern
import scala.io.Source

//noinspection ScalaUnusedSymbol
/**
 * @see [[ScalaBundleSortingTest]]
 */
object ScalaBundleSorting {

  trait ModuleInfoLike {
    def rootPath: Path
    def searcher: Searcher

    def srcPath: Path = rootPath / "src"
    def resourcesPath: Path = rootPath /  "resources"
    def messagesPath: Path = resourcesPath / "messages"
  }

  case class ModuleInfo(
    override val rootPath: Path,
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
    override val rootPath: Path,
    bundleMessagesRelativePath: String,
    extraUsageModules: Seq[ModuleInfo] = Nil,
    override val searcher: Searcher = new Searcher
  ) extends ModuleInfoLike {
    def bundleAbsolutePath: Path = (messagesPath / bundleMessagesRelativePath).normalize()
  }

  val communityDir: Path = TestUtils.findCommunityRootPath
  val scalaModDir: Path = communityDir / "scala"
  val sbtModDir: Path = communityDir / "sbt"
  val scalaImplDir: Path = scalaModDir / "scala-impl"
  val integrationDir: Path = scalaModDir / "integration"

  val allModuleInfos: Seq[ModuleWithBundleInfo] = Seq(
    ModuleWithBundleInfo(
      rootPath = communityDir / "bsp",
      bundleMessagesRelativePath = "ScalaBspBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "codeInsight",
      bundleMessagesRelativePath = "ScalaCodeInsightBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "compile-server",
      bundleMessagesRelativePath = "ScalaCompileServerBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "compiler-integration",
      bundleMessagesRelativePath = "CompilerIntegrationBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "compiler-jps",
      bundleMessagesRelativePath = "ScalaJpsBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "compiler-shared",
      bundleMessagesRelativePath = "ScalaCompileServerSharedBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "conversion",
      bundleMessagesRelativePath = "ScalaConversionBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "debugger",
      bundleMessagesRelativePath = "DebuggerBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "repl",
      bundleMessagesRelativePath = "ScalaReplBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = integrationDir / "devKit",
      bundleMessagesRelativePath = "ScalaDevkitBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = integrationDir / "gradle",
      bundleMessagesRelativePath = "ScalaGradleBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = integrationDir / "intellilang",
      bundleMessagesRelativePath = "ScalaIntellilangBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = integrationDir / "java-decompiler",
      bundleMessagesRelativePath = "ScalaJavaDecompilerBundle.properties",
    ),
//    ModuleWithBundleInfo(
//      rootPath = integrationDir /"packagesearch/",
//      bundleMessagesRelativePath = "PackageSearchSbtBundle.properties",
//    ),
    ModuleWithBundleInfo(
      rootPath = integrationDir / "properties",
      bundleMessagesRelativePath = "ScalaI18nBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = sbtModDir / "sbt-api",
      bundleMessagesRelativePath = "SbtApiBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = sbtModDir / "sbt-impl",
      bundleMessagesRelativePath = "SbtBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaDirectiveBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaEditorBundle.properties",
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaInspectionBundle.properties",
      extraUsageModules = Seq(
        ModuleInfo(integrationDir / "properties")
      )
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaMetaBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaImplDir,
      bundleMessagesRelativePath = "ScalaOptionsBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "structure-view",
      bundleMessagesRelativePath = "ScalaStructureViewBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "test-integration/testing-support",
      bundleMessagesRelativePath = "TestingSupportBundle.properties"
    ),
    ModuleWithBundleInfo(
      rootPath = scalaModDir / "worksheet",
      bundleMessagesRelativePath = "ScalaWorksheetBundle.properties",
    ),
  )

  def main(args: Array[String]): Unit = {
    sortAll(allModuleInfos)
    new ScalaBundleCoverageTest().testAllBundlesAreCovered()
  }

  def sortAll(moduleInfos: Seq[ModuleWithBundleInfo]): Unit = for (moduleInfo <- moduleInfos) {
    val keyToFindings: Map[String, List[Finding]] =
      findKeyUsages(moduleInfo)

    val bundlePath = moduleInfo.bundleAbsolutePath
    println(s"Read bundle $bundlePath")
    val I18nBundleContent(entries) = read(bundlePath.toFile)
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

    def search(path: Path): Seq[String] = {
      val result = Seq.newBuilder[String]
      val scanner = new Scanner(Source.fromFile(path.toFile).bufferedReader())
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

  def findKeysInDirectory(root: Path, searcher: Searcher): List[Finding] =
    for (file <- allFilesIn(root.toFile).toList.sorted; key <- searcher.search(file.toPath)) yield {
      val absoluteFilepath = file.toString.replace('\\', '/')
      val relativeFilepath = absoluteFilepath.substring(root.toString.length).stripPrefix("/")
      Finding(relativeFilepath, key)(absoluteFilepath)
    }

  private def allFilesIn(file: File): Iterator[File] =
    if (!file.exists) Iterator()
    else if (!file.isDirectory) Iterator(file)
    else file.listFiles.sorted.iterator.flatMap(allFilesIn)
}
