package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ScopePriorityTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "scope/priority/"
  }

  def testBlock11 = doTest
  def testBlock12 = doTest
  def testBlock21 = doTest
  def testBlock22 = doTest
  def testBlockAndCount = doTest
  def testBlockAndType = doTest
  def testBlockNested = doTest
  //TODO packageobject
//  def testPackageObject = doTest
}