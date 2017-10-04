package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportElementTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/element/"
  }

  def testCaseClass() {doTest()}
  def testCompanion() {doTest()}
  def testObject() {doTest()}
  def testPackage() {doTest()}
  def testTrait() {doTest()}
  def testFunctionParameter() {doTest()}
  def testInherited() {doTest()}
  def testValue() {doTest()}
  def testVariable() {doTest()}
  def testTypeAlias() {doTest()}
  //TODO classparameter
//  def testCaseClassParameter = doTest
}