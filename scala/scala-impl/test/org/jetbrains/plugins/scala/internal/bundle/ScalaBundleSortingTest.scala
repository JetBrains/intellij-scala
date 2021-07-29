package org.jetbrains.plugins.scala.internal.bundle

import junit.framework.{TestCase, TestSuite}
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.internal.bundle.ScalaBundleSorting._
import org.jetbrains.plugins.scala.internal.bundle.ScalaBundleSortingTest.ActualTest
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent._
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class ScalaBundleSortingTest extends TestSuite {
  locally {
    val tests = ScalaBundleSorting.allModuleInfos.map(new ActualTest(_))
    tests.foreach(this.addTest)
  }
}

object ScalaBundleSortingTest {
  def suite: TestSuite = new ScalaBundleSortingTest

  final class ActualTest(moduleInfo: ModuleInfo) extends ScalaBundleSortingTestBase {
    this.setName(moduleInfo.bundleAbsolutePath)
    override def runTest(): Unit = {
      testDirectory(moduleInfo)
    }
  }
}


abstract class ScalaBundleSortingTestBase extends TestCase with AssertionMatchers {
  def testDirectory(info: ModuleInfo): Unit = {
    val findings = findKeysInModule(info)
    val I18nBundleContent(entries) = read(info.bundleAbsolutePath)

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
