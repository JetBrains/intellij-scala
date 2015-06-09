package org.jetbrains.sbt
package resolvers

import java.io.{IOException, File}

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

/**
 * @author Nikolay Obedin
 * @since 8/1/14.
 */
class ResolverIndexLocalIndexingTest extends ResolverIndexingTestCase with UsefulTestCaseHelper {

  import junit.framework.Assert._

  def testIndexUpdate() = {
    val testIndex = createAndUpdateIndex(SbtResolver(SbtResolver.Kind.Maven, "Test repo", "file:/%s/sbt/resolvers/testRepository" format rootPath))
    assertIndexContentsEquals(testIndex, Set("org.jetbrains"), Set("test-one", "test-two"), Set("0.0.1", "0.0.2"))
  }

  def testNonExistentIndexUpdate() =
    assertException[IOException](Some("Repository directory /non-existent-dir does not exist")) {
      createAndUpdateIndex(SbtResolver(SbtResolver.Kind.Maven, "Test repo", "file:/non-existent-dir"))
    }
}
