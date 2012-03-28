package org.jetbrains.plugins.scala
package lang.actions.editor

import lang.completion3.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Jason Zaugg
 */

class ImportSelectorTypingTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter.CARET_MARKER

  def testImportSelectorAdded() {
    val text = "class A { import scala.collection.mutable" + CARET_MARKER + "\n}"
    val assumedStub = "class A { import scala.collection.{mutable, }\n}"

    checkGeneratedTextAfterTyping(text, assumedStub, ',')
  }

  def testImportSelectorNoChange() {
    val text = "class A { import scala.collection.{mutable}" + CARET_MARKER + "\n}"
    val assumedStub = "class A { import scala.collection.{mutable},\n}"

    checkGeneratedTextAfterTyping(text, assumedStub, ',')
  }

}
