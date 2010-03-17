package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class QualifierSourceMediateTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "qualifier/source/mediate/"
  }

  def testCaseClass = doTest
  def testCaseClassObject = doTest
  //TODO
//  def testCaseClassObjectSyntetic = doTest
  def testCaseObject = doTest
  //TODO
//  def testCaseObjectSyntetic = doTest
  def testClass = doTest
  def testObject = doTest
  def testTrait = doTest
}