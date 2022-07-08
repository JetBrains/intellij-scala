package org.jetbrains.plugins.scala.annotator.gutter

class NewlineTest extends LineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/newline/"

  def testCollision(): Unit = doTest()
  def testJoined(): Unit = doTest()
  def testSpacing(): Unit = doTest()
  def testWrap(): Unit = doTest()
}