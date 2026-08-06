package org.jetbrains.plugins.scala.internal.bundle

import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.plugins.scala.internal.bundle.ScalaBundleSorting._
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent._
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
class ScalaBundleSortingTest {

  @unused("used reflectively by the @Parameters annotation")
  private def testParameters: Array[AnyRef] =
    ScalaBundleSorting.allModuleInfos.toArray.map { bundle =>
      val testName = bundle.bundleMessagesRelativePath.stripSuffix(".properties")
      Array(testName, bundle)
    }

  @Test
  @Parameters(method = "testParameters")
  @TestCaseName(value = "{method}[{0}]")
  def bundleSortingTest(
    @unused("used reflectively by the @TestCaseName annotation") testName: String,
    bundle: ModuleWithBundleInfo
  ): Unit = {
    ScalaBundleSortingTest.checkDirectory(bundle)
  }
}

object ScalaBundleSortingTest {
  def checkDirectory(info: ModuleWithBundleInfo): Unit = {
    val keyToFindings: Map[String, List[Finding]] =
      ScalaBundleSorting.findKeyUsages(info)

    val I18nBundleContent(entries) = read(info.bundleAbsolutePath)

    val usedEntries = entries.filterNot(_.path == unusedPath)

    val tryToRerunSorting_HintSuffix =
      "\nTry to run `ScalaUltimateBundleSorting.main` or `ScalaBundleSorting.main` locally and commit the changes"

    //val undefinedFindings = findings.map(_.key).filterNot(keyToEntry.contains)
    //assert(undefinedFindings.isEmpty, s"Undefined keys(${undefinedFindings.size}): " + undefinedFindings.mkString(", "))
    val noPathEntries = entries.filter(_.path == noPath)
    assert(
      noPathEntries.isEmpty,
      s"""Entries outside of path header: ${noPathEntries.map(_.key).mkString(", ")}$tryToRerunSorting_HintSuffix"""
    )

    val unusedEntries = usedEntries.filterNot(e => keyToFindings.contains(e.key))
    assert(
      unusedEntries.isEmpty,
      s"""Unused bundle keys: ${unusedEntries.map(_.key).mkString(", ")}$tryToRerunSorting_HintSuffix"""
    )

    val notInPathUsed = usedEntries.filterNot(e => keyToFindings(e.key).exists(_.relativeFilepath == e.path))
    assert(
      notInPathUsed.isEmpty,
      s"""Not used in its path: ${notInPathUsed.map(_.key).mkString(", ")}$tryToRerunSorting_HintSuffix"""
    )
  }
}
