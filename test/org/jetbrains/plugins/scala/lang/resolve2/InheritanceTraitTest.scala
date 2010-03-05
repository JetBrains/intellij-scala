package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceTraitTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/trait/"
  }

  //TODO abstractoverride
//  def testAbstractOverrideExtendsClass = doTest
  def testAbstractOverrideExtendsFunction = doTest
  //TODO abstractoverride
//  def testAbstractOverrideSelfClass = doTest
  //TODO abstractoverride
//  def testAbstractOverrideSelfFunction = doTest

  //TODO abstractoverride
//  def testClashAbstractOverrideExtends1 = doTest
  //TODO abstractoverride
//  def testClashAbstractOverrideExtends2 = doTest
  //TODO abstractoverride
//  def testClashAbstractOverrideSelf = doTest

  def testClashTwo1 = doTest
  def testClashTwo2 = doTest
  def testMixOne = doTest
  def testMixTwo = doTest
  def testSelfTypeElements = doTest
  def testSelfTypeModifiers = doTest
}