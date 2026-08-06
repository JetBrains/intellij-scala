package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class InheritanceThisElementTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "inheritance" / "this" / "element"

  //TODO scriptthis
//  def testBlock = doTest
  def testClass(): Unit = doTest()
  //TODO classparameter
//  def testClassParameter = doTest
  def testClassParameterValue(): Unit = doTest()
  def testClassParameterVariable(): Unit = doTest()
  //TODO scriptthis
//  def testFile = doTest
  //TODO scriptthis
//  def testFunction = doTest
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
}
