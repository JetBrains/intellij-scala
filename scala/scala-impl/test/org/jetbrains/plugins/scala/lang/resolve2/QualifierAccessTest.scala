package org.jetbrains.plugins.scala.lang.resolve2

/**
 * Pavel.Fatin, 02.02.2010
 */
class QualifierAccessTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "qualifier/access/"
  }

  def testClassParameterValue() {doTest()}
  def testClassParameterVariable() {doTest()}
  def testPrivateRef() {doTest()}
  def testPrivateRefCaseClass() {doTest()}
  def testPrivateThis() {doTest()}
  def testPrivateThisCaseClass() {doTest()}
  def testSourcePrivate() {doTest()}
  def testSourceProtected() {doTest()}
  def testTargetPrivate() {doTest()}
  def testTargetProtected() {doTest()}
  def testQualifiedAccissibility() {doTest()}
  def testSCL3857() {doTest()}
  def testSelfQualifier() {doTest()}
}