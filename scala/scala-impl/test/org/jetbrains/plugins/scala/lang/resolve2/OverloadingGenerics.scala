package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class OverloadingGenerics extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "overloading/generics/"
  }

  def testDefaultValue() = doTest()
  def testDefaultValue2() = doTest()
  //TODO
//  def testDefaultValue3 = doTest
  def testGenerics1() = doTest()
  def testGenerics2() = doTest()
  //TODO
//  def testGenerics3 = doTest
  def testNoLiteralNarrowing() = doTest()
  def testSimpleGenercs() = doTest()
  def testWeakConforms() = doTest()
}
