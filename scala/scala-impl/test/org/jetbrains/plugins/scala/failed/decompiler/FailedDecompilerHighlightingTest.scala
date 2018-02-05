package org.jetbrains.plugins.scala.failed.decompiler

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category
import org.jetbrains.plugins.scala.lang.highlighting.decompiler.DecompilerHighlightingTestBase


/**
  * @author Roman.Shein
  * @since 13.04.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class FailedDecompilerHighlightingTest extends DecompilerHighlightingTestBase {

  override protected def shouldPass: Boolean = false

  def testTypers() = doTest("Typers.class")

  def testReifiers() = doTest("Reifiers.class")

  def testParsers() = doTest("Parsers.class")

  def testReference() = doTest("Reference.class")

  def testScaladocModelTest() = doTest("ScaladocModelTest.class")

  def testClassloadVerify() = doTest("ClassloadVerify.class")

  def testScalac() = doTest("Scalac.class")

  def testScaladoc() = doTest("Scaladoc.class")

  def testPathResolver() = doTest("PathResolver.class")

  def testDocParser() = doTest("DocParser.class")
}
