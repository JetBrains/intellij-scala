package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ScopeElementTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "scope/element/"
  }

  def testBlock(): Unit = doTest()
  def testCaseClass(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
}