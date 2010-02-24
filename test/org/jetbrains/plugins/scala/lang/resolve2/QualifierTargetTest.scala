package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class QualifierTargetTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "qualifier/target/"
  }

  def testCaseClass = doTest
  def testCaseObject = doTest
  def testClass = doTest
  def testFunction = doTest
  def testImport = doTest
  def testObject = doTest
  def testTrait = doTest
  def testTypeAlias = doTest
}