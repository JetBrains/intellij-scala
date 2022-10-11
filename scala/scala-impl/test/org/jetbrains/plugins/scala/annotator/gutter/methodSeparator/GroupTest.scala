package org.jetbrains.plugins.scala.annotator.gutter.methodSeparator

class GroupTest extends MethodSeparatorLineMarkerTestBase {
  protected override def getBasePath = super.getBasePath + "/group/"

  def testSolid(): Unit = doTest()
  def testSeparated(): Unit = doTest()
  def testMixed(): Unit = doTest()
  def testMixedLine(): Unit = doTest()
  def testStatement(): Unit = doTest()
}