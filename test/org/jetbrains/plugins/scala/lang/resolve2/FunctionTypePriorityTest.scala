package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class FunctionTypePriorityTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "function/type/priority/"
  }

  def testInheritanceHierarchy() = doTest()
  //TODO answer?
//  def testInheritanceIncompatible = doTest
  def testInheritanceOne1() = doTest()
  def testInheritanceOne2() = doTest()
  def testInheritanceTwo1() = doTest()
  def testInheritanceTwo2() = doTest()
}