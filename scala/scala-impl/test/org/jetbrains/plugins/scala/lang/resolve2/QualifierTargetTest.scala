package org.jetbrains.plugins.scala.lang.resolve2

class QualifierTargetTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "qualifier/target/"
  }

  def testCaseClass(): Unit = doTest()
  def testCaseObject(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testClassParameter(): Unit = doTest()
  def testClassParameterValue(): Unit = doTest()
  def testClassParameterVariable(): Unit = doTest()
  def testClassTypeParameter(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testImport(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testTypeAlias(): Unit = doTest()
}