package org.jetbrains.sbt
package resolvers

import java.io.File

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

/**
 * @author Nikolay Obedin
 * @since 8/22/14.
 */
class ResolverIvyCacheTest extends ScalaFixtureTestCase {
  import _root_.junit.framework.Assert._

  def testIndexing() = {
    val testResolver = new SbtResolver(SbtResolver.Kind.Ivy, "Test repo", "/%s/sbt/resolvers/testIvyCache" format rootPath)
    val tmpPath = new File(myFixture.getTempDirPath)
    val storingManager = new SbtResolverIndexesManager(Some(tmpPath))
    val newIndex = storingManager.add(testResolver)
    newIndex.update(None)

    val groups = Set("org.jetbrains")
    val artifacts = Set("test-one", "test-two")
    val versions = Set("0.0.1", "0.0.2")

    assertEquals(newIndex.groups(), groups)
    assertEquals(newIndex.artifacts(), artifacts)
    artifacts foreach { a => assertEquals(newIndex.versions(groups.head, a), versions) }

    storingManager.dispose()
  }
}
