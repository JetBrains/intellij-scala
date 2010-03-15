package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class BasedFileTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "dir/"
  }

  def testFileBased = doTest
}