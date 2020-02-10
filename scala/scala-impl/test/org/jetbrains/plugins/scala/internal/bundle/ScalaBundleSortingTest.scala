package org.jetbrains.plugins.scala
package internal
package bundle

import junit.framework.TestCase
import org.jetbrains.plugins.scala.internal.bundle.ScalaBundleSorting._
import org.jetbrains.plugins.scala.util.internal.SortedScalaStringBundle
import org.jetbrains.plugins.scala.util.internal.SortedScalaStringBundle._

/**
 * If this fails maybe run the main method in [[ScalaBundleSorting]]
 */
class ScalaBundleSortingTest extends TestCase with AssertionMatchers {
  def test_ScalaImpl(): Unit = testDirectory(scalaImplModule)

  def testDirectory(info: ModuleInfo): Unit = {
    val ModuleInfo(rootPath, bundlePath, searcher) = info
    val findings = findKeysInModule(rootPath, searcher)
    val SortedScalaStringBundle(entries) = readBundle(bundlePath)

    val keyToFinding = findings.groupBy(_.key)

    val usedEntries = entries.filterNot(_.path == unusedCategoryPath)

    //val undefinedFindings = findings.map(_.key).filterNot(keyToEntry.contains)
    //assert(undefinedFindings.isEmpty, s"Undefined keys(${undefinedFindings.size}): " + undefinedFindings.mkString(", "))

    val unusedEntries = usedEntries.filterNot(e => keyToFinding.contains(e.key))
    assert(unusedEntries.isEmpty, "Unused bundle keys: " + unusedEntries.map(_.key).mkString(", "))

    val notInPathUsed = usedEntries.filterNot(e => keyToFinding(e.key).exists(_.relativeFilepath == e.path))
    assert(notInPathUsed.isEmpty, "Not used in it's path: " + notInPathUsed.map(_.key).mkString(", "))
  }
}
