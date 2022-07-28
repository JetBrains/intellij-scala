package org.jetbrains.plugins.scala.lang.resolve2

class InheritanceThisNestedTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "inheritance/this/nested/"
  }

  def testClass(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testClashClass(): Unit = doTest()
  def testClashObject(): Unit = doTest()
  def testClashTrait(): Unit = doTest()
  def testQualifiedClass(): Unit = doTest()
  def testQualifiedObject(): Unit = doTest()
  def testQualifiedTrait(): Unit = doTest()
  //TODO answer?
//  def testWrongQualifier = doTest
}