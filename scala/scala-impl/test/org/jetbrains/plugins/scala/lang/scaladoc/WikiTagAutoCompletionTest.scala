package org.jetbrains.plugins.scala.lang.scaladoc

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class WikiTagAutoCompletionTest extends EditorActionTestBase {

  def testCodeLinkAC(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** [${|} */",
      "/** [[]] */",
      '['
    )

  def testInnerCodeAC(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** {{${|} */",
      "/** {{{}}} */",
      '{'
    )

  def testMonospaceAC(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** ${|} */",
      "/** `` */",
      '`'
    )

  def testSuperscriptAC(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** ${|} */",
      "/** ^^ */",
      '^'
    )

  def testSubscriptAC(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** ,${|} */",
      "/** ,,,, */",
      ','
    )

  def testBoldSimpleAC(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** ''${|}'' */",
      "/** '''''' */",
      '\''
    )
}
