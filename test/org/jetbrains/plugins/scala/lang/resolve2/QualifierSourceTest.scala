package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class QualifierSourceTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "qualifier/source/"
  }

  def testCaseClass = doTest
  //TODO
//  def testCaseClassObject = doTest
  //TODO
//  def testCaseClassObjectSyntetic = doTest
  def testCaseObject = doTest
//  def testCaseObjectSyntetic = doTest
  //TODO
//  def testChain = doTest
  def testClass = doTest
  def testObject = doTest
  //TODO
//  def testPackage = doTest
  //TODO
//  def testPackageAsValue = doTest
  //TODO
//  def testPackageObject = doTest
  //TODO
//  def testPackageObjectAsValue = doTest
  def testTrait = doTest
}