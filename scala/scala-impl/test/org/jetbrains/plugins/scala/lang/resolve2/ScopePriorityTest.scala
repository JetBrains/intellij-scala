package org.jetbrains.plugins.scala.lang.resolve2

class ScopePriorityTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "scope/priority/"
  }

  def testBlock11(): Unit = doTest()
  def testBlock12(): Unit = doTest()
  def testBlock21(): Unit = doTest()
  def testBlock22(): Unit = doTest()
  def testBlockAndCount(): Unit = doTest()
  def testBlockAndType(): Unit = doTest()
  def testBlockNested(): Unit = doTest()
  //TODO packageobject
//  def testPackageObject = doTest
}