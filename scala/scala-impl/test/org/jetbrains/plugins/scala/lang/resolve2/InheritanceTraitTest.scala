package org.jetbrains.plugins.scala.lang.resolve2

class InheritanceTraitTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/trait/"
  }

  //TODO abstractoverride
//  def testAbstractOverrideExtendsClass = doTest
  def testAbstractOverrideExtendsFunction(): Unit = doTest()
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

  def testClashTwo1(): Unit = doTest()
  def testClashTwo2(): Unit = doTest()
  def testMixOne(): Unit = doTest()
  def testMixTwo(): Unit = doTest()
  def testSelfTypeElements(): Unit = doTest()
  def testSelfTypeModifiers(): Unit = doTest()
}