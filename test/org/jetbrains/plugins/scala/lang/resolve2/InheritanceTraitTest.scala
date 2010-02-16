package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceTraitTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/trait/"
  }

  //TODO
//  def testAbstractOverrideExtendsClass = doTest
  def testAbstractOverrideExtendsFunction = doTest
  //TODO
//  def testAbstractOverrideSelfClass = doTest
  //TODO
//  def testAbstractOverrideSelfFunction = doTest

  //TODO
//  def testClashAbstractOverrideExtends1 = doTest
  //TODO
//  def testClashAbstractOverrideExtends2 = doTest
  //TODO
//  def testClashAbstractOverrideSelf = doTest

  //TODO
//  def testClashTwo1 = doTest
  //TODO
//  def testClashTwo2 = doTest

  def testMixOne = doTest
  def testMixTwo = doTest

  def testSelfTypeElements = doTest
  def testSelfTypeModifiers = doTest
}