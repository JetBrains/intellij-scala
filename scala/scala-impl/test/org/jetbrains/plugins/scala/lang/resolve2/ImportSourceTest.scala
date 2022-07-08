package org.jetbrains.plugins.scala.lang.resolve2

class ImportSourceTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/source/"
  }

  //TODO caseclass
//  def testCaseClass = doTest
  def testClass(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testPackage(): Unit = doTest()
  //TODO packageobject
//  def testPackageObject = doTest
  //TODO packageobject
//  def testPackageWithObject = doTest
  def testPackageNested(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testVariable(): Unit = doTest()
}