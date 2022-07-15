package org.jetbrains.plugins.scala
package lang.scaladoc

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
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
