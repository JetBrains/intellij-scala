package org.jetbrains.plugins.scala.lang.resolve2

class ImportClashTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/clash/"
  }

  def testFunction1(): Unit = doTest()
  def testFunction2(): Unit = doTest()
  def testFunction3(): Unit = doTest()
  def testType1(): Unit = doTest()
  def testType2(): Unit = doTest()
  def testType3(): Unit = doTest()
  def testTypeAndValue1(): Unit = doTest()
  def testTypeAndValue2(): Unit = doTest()
  def testTypeAndValue3(): Unit = doTest()
  def testTypeAndValueAll(): Unit = doTest()
  def testValue1(): Unit = doTest()
  def testValue2(): Unit = doTest()
  def testValue3(): Unit = doTest()
}