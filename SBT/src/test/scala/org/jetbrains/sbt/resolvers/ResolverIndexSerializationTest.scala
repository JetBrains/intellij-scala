package org.jetbrains.sbt
package resolvers

import java.io.File

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

/**
 * @author Nikolay Obedin
 * @since 7/28/14.
 */
class ResolverIndexSerializationTest extends ScalaFixtureTestCase {

  val testResolver = new SbtResolver("some cool repo name", "http://some.cool.repo/")

  def testIndexStoring() = {
    val tmpPath = new File(myFixture.getTempDirPath)
    val storingManager = new SbtResolverIndexesManager(tmpPath)
    storingManager.update(Seq(testResolver))

    val loadingManager = new SbtResolverIndexesManager(tmpPath)
    val indexOpt = loadingManager.find(testResolver)
    assert(indexOpt.isDefined)

    import _root_.junit.framework.Assert._
    val index = indexOpt.get
    assertEquals(index.root, testResolver.root)
    assertEquals(index.timestamp, SbtResolverIndex.NO_TIMESTAMP)
  }

  def testIndexLoading() = {
    myFixture.setTestDataPath(rootPath)
    val testIndexDir = new File(myFixture.copyDirectoryToProject("sbt/resolvers/testIndex", "testIndex").getPath)
    val manager = new SbtResolverIndexesManager(testIndexDir)

    val indexOpt = manager.find(testResolver)
    assert(indexOpt.isDefined)

    import _root_.junit.framework.Assert._
    val index = indexOpt.get
    assertEquals(index.root, testResolver.root)
    assertEquals(index.timestamp, SbtResolverIndex.NO_TIMESTAMP)
  }
}
