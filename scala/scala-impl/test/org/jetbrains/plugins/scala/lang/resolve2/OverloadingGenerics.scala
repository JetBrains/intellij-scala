package org.jetbrains.plugins.scala.lang.resolve2

class OverloadingGenerics extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "overloading/generics/"
  }

  def testDefaultValue(): Unit = doTest()
  def testDefaultValue2(): Unit = doTest()
  //TODO
//  def testDefaultValue3 = doTest
  def testGenerics1(): Unit = doTest()
  def testGenerics2(): Unit = doTest()
  //TODO
//  def testGenerics3 = doTest
  def testNoLiteralNarrowing(): Unit = doTest()
  def testSimpleGenercs(): Unit = doTest()
  def testWeakConforms(): Unit = doTest()
}
