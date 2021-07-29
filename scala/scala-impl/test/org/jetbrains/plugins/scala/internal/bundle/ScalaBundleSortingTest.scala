package org.jetbrains.plugins.scala
package internal
package bundle

import junit.framework.TestCase
import org.jetbrains.plugins.scala.internal.bundle.ScalaBundleSorting._
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent._

/**
 * If this fails maybe run the main method in [[ScalaBundleSorting]]
 */
class ScalaBundleSortingTest extends ScalaBundleSortingTestBase {
  def test_bspModule(): Unit = testDirectory(bspModule)
  def test_codeInsightModule(): Unit = testDirectory(codeInsightModule)
  def test_conversionModule(): Unit = testDirectory(conversionModule)
  def test_devkitModule(): Unit = testDirectory(devkitModule)
  def test_compilerJpsModule(): Unit = testDirectory(compilerJpsModule)
  def test_compilerSharedModule(): Unit = testDirectory(compilerSharedModule)
  def test_macrosModule(): Unit = testDirectory(macrosModule)
  def test_scalaImplModule(): Unit = testDirectory(scalaImplModule)
  def test_scalaImplModuleErrMsg(): Unit = testDirectory(scalaImplModuleErrMsg)
  def test_scalaImplModuleCodeInspection(): Unit = testDirectory(scalaImplModuleCodeInspection)
  def test_uastModule(): Unit = testDirectory(uastModule)
  def test_worksheetModule(): Unit = testDirectory(worksheetModule)
}

abstract class ScalaBundleSortingTestBase extends TestCase with AssertionMatchers {
  def testDirectory(info: ModuleInfo): Unit = {
    val findings = findKeysInModule(info)
    val I18nBundleContent(entries) = read(info.bundlePath)

    val keyToFinding = findings.groupBy(_.key)

    val usedEntries = entries.filterNot(_.path == unusedPath)

    //val undefinedFindings = findings.map(_.key).filterNot(keyToEntry.contains)
    //assert(undefinedFindings.isEmpty, s"Undefined keys(${undefinedFindings.size}): " + undefinedFindings.mkString(", "))
    val noPathEntries = entries.filter(_.path == noPath)
    assert(noPathEntries.isEmpty, "Entries outside of path header: " + noPathEntries.map(_.key).mkString(", "))

    val unusedEntries = usedEntries.filterNot(e => keyToFinding.contains(e.key))
    assert(unusedEntries.isEmpty, "Unused bundle keys: " + unusedEntries.map(_.key).mkString(", "))

    val notInPathUsed = usedEntries.filterNot(e => keyToFinding(e.key).exists(_.relativeFilepath == e.path))
    assert(notInPathUsed.isEmpty, "Not used in it's path: " + notInPathUsed.map(_.key).mkString(", "))
  }
}
