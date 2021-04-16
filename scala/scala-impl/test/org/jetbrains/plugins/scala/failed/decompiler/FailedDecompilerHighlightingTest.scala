package org.jetbrains.plugins.scala.failed.decompiler

import org.jetbrains.plugins.scala.lang.highlighting.decompiler.DecompilerHighlightingTestBase


/**
  * @author Roman.Shein
  * @since 13.04.2016.
  */
class FailedDecompilerHighlightingTest extends DecompilerHighlightingTestBase {

  override protected def shouldPass: Boolean = false

  def testTypers(): Unit = doTest("Typers.class")

  def testReifiers(): Unit = doTest("Reifiers.class")

  def testParsers(): Unit = doTest("Parsers.class")

  def testReference(): Unit = doTest("Reference.class")

  def testScaladocModelTest(): Unit = doTest("ScaladocModelTest.class")

  def testClassloadVerify(): Unit = doTest("ClassloadVerify.class")

  def testScalac(): Unit = doTest("Scalac.class")

  def testScaladoc(): Unit = doTest("Scaladoc.class")

  def testPathResolver(): Unit = doTest("PathResolver.class")

  def testDocParser(): Unit = doTest("DocParser.class")
}
