package org.jetbrains.plugins.scala.lang.resolve2

class ImportRelationTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/relation/"
  }

  def testAbsolute(): Unit = doTest()
  def testClash(): Unit = doTest()
  def testRelative(): Unit = doTest()
  def testRoot(): Unit = doTest()
}