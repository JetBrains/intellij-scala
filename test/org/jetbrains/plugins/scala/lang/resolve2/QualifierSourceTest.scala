package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class QualifierSourceTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "qualifier/source/"
  }

  def testCaseClass = doTest
  //TODO caseclass
//  def testCaseClassObject = doTest
  //TODO caseclass
//  def testCaseClassObjectSyntetic = doTest
  def testCaseObject = doTest
  //TODO caseclass
//  def testCaseObjectSyntetic = doTest
  def testChain = doTest
  def testClass = doTest
  def testObject = doTest
  def testPackage = doTest
  //TODO getClass
//  def testPackageAsValue = doTest
  //TODO packageobject
//  def testPackageObject = doTest
  //TODO packageobject
//  def testPackageObjectAsValue = doTest
  def testTrait = doTest
}