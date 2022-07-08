package org.jetbrains.plugins.scala.lang.resolve2

class FunctionNamedTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/named/"
  }

  def testClash1(): Unit = doTest()
  def testClash2(): Unit = doTest()
  def testClash3(): Unit = doTest()
  def testExcess1(): Unit = doTest()
  def testExcess2(): Unit = doTest()
  def testNoName1(): Unit = doTest()
  def testNoName2(): Unit = doTest()
  def testOrderNormal(): Unit = doTest()
  def testOrderReverted(): Unit = doTest()
  def testUnnamed1(): Unit = doTest()
  //TODO
//  def testUnnamed2 = doTest
  def testUnresolved(): Unit = doTest()
}