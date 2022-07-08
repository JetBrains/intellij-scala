package org.jetbrains.plugins.scala.lang.resolve2

class QualifierAccessTest extends ResolveTestBase {

  override def folderPath: String =
    super.folderPath + "qualifier/access/"

  def testClassParameterValue(): Unit = doTest()
  def testClassParameterVariable(): Unit = doTest()
  def testPrivateRef(): Unit = doTest()
  def testPrivateRefCaseClass(): Unit = doTest()
  def testPrivateThis(): Unit = doTest()
  def testPrivateThisCaseClass(): Unit = doTest()
  def testSourcePrivate(): Unit = doTest()
  def testSourceProtected(): Unit = doTest()
  def testTargetPrivate(): Unit = doTest()
  def testTargetProtected(): Unit = doTest()
  def testQualifiedAccissibility(): Unit = doTest()
  def testSCL3857(): Unit = doTest()
  def testSelfQualifier(): Unit = doTest()
}