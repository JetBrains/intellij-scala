package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class FunctionTypePriorityTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "function" / "type" / "priority"

  def testInheritanceHierarchy(): Unit = doTest()
  //TODO answer?
//  def testInheritanceIncompatible = doTest
  def testInheritanceOne1(): Unit = doTest()
  def testInheritanceOne2(): Unit = doTest()
  def testInheritanceTwo1(): Unit = doTest()
  def testInheritanceTwo2(): Unit = doTest()
}
