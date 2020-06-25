package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class PredefClashTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "predef/clash/"
  }

  def testInherited(): Unit = doTest()
  def testLocal1(): Unit = doTest()
  def testLocal2(): Unit = doTest()
  def testOuterScope(): Unit = doTest()
}