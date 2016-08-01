package org.jetbrains.sbt.resolvers

import java.io.File

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.junit.Assert._

/**
 * @author Nikolay Obedin
 * @since 6/9/15.
 */
abstract class IndexingTestCase extends ScalaFixtureTestCase {

  var storingManager: SbtResolverIndexesManager = null

  override def setUp(): Unit = {
    super.setUp()
    val tmpPath = new File(myFixture.getTempDirPath)
    storingManager = new SbtResolverIndexesManager(Some(tmpPath))
  }

  override def tearDown(): Unit = {
    super.tearDown()
    storingManager.dispose()
  }

  def createAndUpdateIndex(resolver: SbtResolver): SbtResolverIndex = {
    val index = storingManager.add(resolver)
    index.update(None)
    index
  }

  def assertIndexContentsEquals(index: SbtResolverIndex, groups: Set[String], artifacts: Set[String], versions: Set[String]): Unit = {
    assertEquals(index.groups(), groups)
    assertEquals(index.artifacts(), artifacts)
    artifacts foreach { a => assertEquals(index.versions(groups.head, a), versions) }
  }
}
