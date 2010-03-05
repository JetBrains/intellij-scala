package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportSourceTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/source/"
  }

  //TODO caseclass
//  def testCaseClass = doTest
  def testClass = doTest
  def testObject = doTest
  def testPackage = doTest
  //TODO packageobject
//  def testPackageObject = doTest
  //TODO packageobject
//  def testPackageWithObject = doTest
  def testPackageNested = doTest
  def testTrait = doTest
  def testFunction = doTest
  def testValue = doTest
  def testVariable = doTest
}