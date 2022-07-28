package org.jetbrains.plugins.scala.lang.resolve2

class InheritanceElementTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/element/"
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
  def testValue(): Unit = doTest()
  def testVariable(): Unit = doTest()

  def testCaseClassProduct(): Unit = doTest()
}