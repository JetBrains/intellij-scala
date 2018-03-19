package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.annotator.LiteralTypesHighlightingTestBase
import org.jetbrains.plugins.scala.util.TestUtils

class LiteralTypesHighlightingTest extends LiteralTypesHighlightingTestBase {
  override protected def shouldPass = false
  def folderPath = TestUtils.getTestDataPath + "/annotator/literalTypes/failed/"

  //TODO doesn't work without literal types either
  def testSip23Bounds(): Unit = doTest()

  //TODO doesn't work because of expected type being lost when checking conformance
  def testSip23NamedDefault(): Unit = doTest()

  def testSip23Narrow(): Unit = doTest()

  //TODO highlights properly, but lacks dependencies, add later
  def testSip23Macros1(): Unit = doTest()

  //TODO 'Macros' does not highlight properly at all, fix this later
  def testSip23Test2(): Unit = doTest()
}
