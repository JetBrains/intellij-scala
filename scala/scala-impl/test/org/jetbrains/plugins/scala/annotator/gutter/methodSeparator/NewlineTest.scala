package org.jetbrains.plugins.scala.annotator.gutter.methodSeparator

class NewlineTest extends MethodSeparatorLineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/newline/"

  def testCollision(): Unit = doTest()
  def testJoined(): Unit = doTest()
  def testSpacing(): Unit = doTest()
  def testWrap(): Unit = doTest()
}