package org.jetbrains.sbt.resolvers

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex
import org.junit.Assert._

/**
 * @author Nikolay Obedin
 * @since 6/9/15.
 */
abstract class IndexingTestCase extends ScalaFixtureTestCase {

  override def setUp(): Unit = {
    super.setUp()
    System.setProperty("ivy.test.indexes.dir", myFixture.getTempDirPath)
  }

  override def tearDown(): Unit = {
    super.tearDown()
  }

  def assertIndexContentsEquals(index: ResolverIndex, groups: Set[String], artifacts: Set[String], versions: Set[String]): Unit = {
    assertEquals(index.searchGroup(), groups)
    assertEquals(index.searchArtifact(), artifacts)
    artifacts foreach { a => assertEquals(index.searchVersion(groups.head, a), versions) }
  }
}
