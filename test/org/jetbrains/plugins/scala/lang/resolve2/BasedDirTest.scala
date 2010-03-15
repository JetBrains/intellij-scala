package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class BasedDirTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "dir/"
  }

  override def allSourcesFromDirectory = true

  def testDirBased = doTest
}