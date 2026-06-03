package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class QualifierSourceMediateTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "qualifier" / "source" / "mediate"

  def testCaseClass(): Unit = doTest()
  def testCaseClassObject(): Unit = doTest()
  //TODO
//  def testCaseClassObjectSyntetic = doTest
  def testCaseObject(): Unit = doTest()
  //TODO
//  def testCaseObjectSyntetic = doTest
  def testClass(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
}
