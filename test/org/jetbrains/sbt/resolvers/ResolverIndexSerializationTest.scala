package org.jetbrains.sbt
package resolvers

import java.io.File

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

/**
 * @author Nikolay Obedin
 * @since 7/28/14.
 */
class ResolverIndexSerializationTest extends ScalaFixtureTestCase {

  import _root_.junit.framework.Assert._

  val testResolver = new SbtResolver(SbtResolver.Kind.Maven, "some cool repo name", "http://some.cool.repo/")

  def testIndexStoring() = {
    val tmpPath = new File(myFixture.getTempDirPath)
    val storingManager = new SbtResolverIndexesManager(Some(tmpPath))
    val newIndex = storingManager.add(testResolver)
    newIndex.store()

    val loadingManager = new SbtResolverIndexesManager(Some(tmpPath))
    val indexOpt = loadingManager.find(testResolver)
    assert(indexOpt.isDefined)

    val index = indexOpt.get
    assertEquals(index.root, testResolver.root)
    assertEquals(index.timestamp, SbtResolverIndex.NO_TIMESTAMP)

    storingManager.dispose()
  }

  def testIndexLoading() = {
    myFixture.setTestDataPath(rootPath)
    val testIndexDir = new File(myFixture.copyDirectoryToProject("sbt/resolvers/testIndex", "testIndex").getPath)
    val storingManager = new SbtResolverIndexesManager(Some(testIndexDir))

    val indexOpt = storingManager.find(testResolver)
    assert(indexOpt.isDefined)

    val index = indexOpt.get
    assertEquals(index.root, testResolver.root)
    assertEquals(index.timestamp, SbtResolverIndex.NO_TIMESTAMP)

    storingManager.dispose()
  }
}
