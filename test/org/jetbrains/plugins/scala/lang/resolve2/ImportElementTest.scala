package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportElementTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/element/"
  }

  def testCompanion = doTest
}