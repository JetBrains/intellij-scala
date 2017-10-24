package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class TypeTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "type/"
  }

  def testClassParameter() = doTest()
  def testClassTypeParameter() = doTest()
  def testDependentMethodTypeBound() = doTest()
  def testFunction() = doTest()
  def testFunctionParameter() = doTest()
  def testFunctionTypeParameter() = doTest()
  def testValue() = doTest()
  def testVariable() = doTest()
  def testThis() = doTest()
  def testTypeProjection() = doTest()
}