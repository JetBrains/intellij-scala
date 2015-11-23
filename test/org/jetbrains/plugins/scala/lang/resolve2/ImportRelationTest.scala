package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportRelationTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/relation/"
  }

  def testAbsolute() = doTest()
  def testClash() = doTest()
  def testRelative() = doTest()
  def testRoot() = doTest()
}