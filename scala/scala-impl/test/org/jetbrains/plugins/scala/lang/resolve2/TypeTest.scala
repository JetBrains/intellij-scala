package org.jetbrains.plugins.scala.lang.resolve2

class TypeTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "type/"
  }

  def testClassParameter(): Unit = doTest()
  def testClassTypeParameter(): Unit = doTest()
  def testDependentMethodTypeBound(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testFunctionParameter(): Unit = doTest()
  def testFunctionTypeParameter(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testVariable(): Unit = doTest()
  def testThis(): Unit = doTest()
  def testTypeProjection(): Unit = doTest()
}