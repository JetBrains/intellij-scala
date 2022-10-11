package org.jetbrains.plugins.scala.annotator.gutter.methodSeparator

class CountTest extends MethodSeparatorLineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/count/"

  def testCount0(): Unit = doTest()
  def testCount1(): Unit = doTest()
  def testCount2(): Unit = doTest()
  def testCount3(): Unit = doTest()
  def testCount4(): Unit = doTest()
}