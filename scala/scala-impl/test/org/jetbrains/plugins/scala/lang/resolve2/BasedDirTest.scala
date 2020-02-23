package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class BasedDirTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "dir/"
  }

  def testDirBased(): Unit = doTest()
}