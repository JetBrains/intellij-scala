package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportRelationTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/relation/"
  }

  def testAbsolute(): Unit = doTest()
  def testClash(): Unit = doTest()
  def testRelative(): Unit = doTest()
  def testRoot(): Unit = doTest()
}