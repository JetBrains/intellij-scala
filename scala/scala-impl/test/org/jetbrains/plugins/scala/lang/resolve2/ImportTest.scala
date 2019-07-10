package org.jetbrains.plugins.scala
package lang
package resolve2


abstract class ImportTestBase extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/"
  }
}

class ImportTest extends ImportTestBase {

  def testBrokenChain(): Unit = doTest()
  def testLocal1(): Unit = doTest()
  def testLocal2(): Unit = doTest()
  def testRedundantImport(): Unit = doTest()
  def testRenamed(): Unit = doTest()
  def testSingle(): Unit = doTest()
  def testHardImport(): Unit = doTest()
}

class ImportTest_without_AbstractMap extends ImportTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version <= Scala_2_10

  def testSelection(): Unit = doTest()
}