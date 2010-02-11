package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class InheritanceThisNestedTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "inheritance/this/nested/"
  }

  def testClass = doTest
  def testObject = doTest
  def testTrait = doTest

  def testClashClass = doTest
  def testClashObject = doTest
  def testClashTrait = doTest

  def testQualifiedClass = doTest
  def testQualifiedObject = doTest
  def testQualifiedTrait = doTest

  //TODO
//  def testWrongQualifier = doTest
}