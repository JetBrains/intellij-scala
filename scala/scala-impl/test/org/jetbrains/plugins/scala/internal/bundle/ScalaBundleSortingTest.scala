package org.jetbrains.plugins.scala.internal.bundle

import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.BundleSortingTests
import org.jetbrains.plugins.scala.internal.bundle.ScalaBundleSorting._
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent._
import org.junit.experimental.categories.Category

/**
 * @see [[ScalaBundleSorting]]
 */
class ScalaBundleSortingTest extends TestCase

object ScalaBundleSortingTest {
  def suite(): Test = {
    val suite = new TestSuite()
    ScalaBundleSorting.allModuleInfos.map(new ActualTest(_)).foreach(suite.addTest)
    suite
  }

  @Category(Array(classOf[BundleSortingTests]))
  final class ActualTest(moduleInfo: ModuleWithBundleInfo) extends TestCase with AssertionMatchers {

    this.setName(moduleInfo.bundleAbsolutePath)

    // Unused, but needed to suppress inspection that JUnit test class cannot be constructed.
    private[ScalaBundleSortingTest] def this() = this(null)

    override def runTest(): Unit = {
      checkDirectory(moduleInfo)
    }

    private def checkDirectory(info: ModuleWithBundleInfo): Unit = {
      val keyToFindings: Map[String, List[Finding]] =
        ScalaBundleSorting.findKeyUsages(info)

      val I18nBundleContent(entries) = read(info.bundleAbsolutePath)

      val usedEntries = entries.filterNot(_.path == unusedPath)

      val tryToRerunSorting_HintSuffix =
        "\nTry to run `ScalaUltimateBundleSorting.main` or `ScalaBundleSorting.main` locally and commit the changes"

      //val undefinedFindings = findings.map(_.key).filterNot(keyToEntry.contains)
      //assert(undefinedFindings.isEmpty, s"Undefined keys(${undefinedFindings.size}): " + undefinedFindings.mkString(", "))
      val noPathEntries = entries.filter(_.path == noPath)
      assert(noPathEntries.isEmpty, "Entries outside of path header: " + noPathEntries.map(_.key).mkString(", ") + tryToRerunSorting_HintSuffix)

      val unusedEntries = usedEntries.filterNot(e => keyToFindings.contains(e.key))
      assert(unusedEntries.isEmpty, "Unused bundle keys: " + unusedEntries.map(_.key).mkString(", ") + tryToRerunSorting_HintSuffix)

      val notInPathUsed = usedEntries.filterNot(e => keyToFindings(e.key).exists(_.relativeFilepath == e.path))
      assert(notInPathUsed.isEmpty, "Not used in its path: " + notInPathUsed.map(_.key).mkString(", ") + tryToRerunSorting_HintSuffix)
    }
  }
}
