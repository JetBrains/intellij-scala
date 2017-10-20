package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class UnresolvedTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "unresolved/"
  }

  def testNamedParameter() = doTest()
  def testFunction() = doTest()
  def testRef() = doTest()
  def testType() = doTest()
}