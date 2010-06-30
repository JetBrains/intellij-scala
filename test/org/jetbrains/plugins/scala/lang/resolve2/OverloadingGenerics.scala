package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class OverloadingGenerics extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "overloading/generics/"
  }

  def testDefaultValue = doTest
  def testDefaultValue2 = doTest
  //TODO
//  def testDefaultValue3 = doTest
  def testGenerics1 = doTest
  def testGenerics2 = doTest
  def testNoLiteralNarrowing = doTest
  def testSimpleGenercs = doTest
  def testWeakConforms = doTest
}
