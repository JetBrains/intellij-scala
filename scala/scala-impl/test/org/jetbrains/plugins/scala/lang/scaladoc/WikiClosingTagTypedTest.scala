package org.jetbrains.plugins.scala.lang.scaladoc

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class WikiClosingTagTypedTest extends EditorActionTestBase {

  def testCodeLinkClosingTagInput(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** [[java.lang.String${|}]] */",
      s"/** [[java.lang.String]${|}] */",
      ']'
    )

  def testInnerCodeClosingTagInput(): Unit =
    checkGeneratedTextAfterTyping(
      s"""  /**
         |    *
         |    * {{{
         |    *  class A {
         |    *    def f(): Unit = {}
         |    * } }}${|}}
         |    */
         |""".stripMargin,
      s"""  /**
         |    *
         |    * {{{
         |    *  class A {
         |    *    def f(): Unit = {}
         |    * } }}}${|}
         |    */
         |""".stripMargin,
      '}'
    )

  def testItalicClosingTagInput(): Unit =
    checkGeneratedTextAfterTyping(
      s""" /**
         |   * ''blah blah blah blah
         |   *   blah blah blah '${|}'
         |   */
         |""".stripMargin,
      s""" /**
         |   * ''blah blah blah blah
         |   *   blah blah blah ''${|}
         |   */
         |""".stripMargin,
      '\''
    )

  def testSuperscriptClosingTagInput(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** 2^2${|}^ = 4 */",
      s"/** 2^2^${|} = 4 */",
      '^'
    )

  def testMonospaceClosingTag(): Unit =
    checkGeneratedTextAfterTyping(
      s""" /**
         |   * `blah-blah${|}`
         |   */
         |""".stripMargin,
      s""" /**
         |   * `blah-blah`${|}
         |   */
         |""".stripMargin,
      '`'
    )

  def testBoldClosingTag(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** '''blah blah blah'${|}'' */",
      s"/** '''blah blah blah''${|}' */",
      '\''
    )

  def testUnderlinedClosingTag(): Unit =
    checkGeneratedTextAfterTyping(
      s""" /**
         |   * __blah blahblahblahblahblah
         |   *       blah blah blah blah${|}__
         |   */
         |""".stripMargin,
      s""" /**
         |   * __blah blahblahblahblahblah
         |   *       blah blah blah blah_${|}_
         |   */
         |""".stripMargin,
      '_'
    )

  def testBoldTagEmpty(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** '''${|}''' */",
      s"/** ''''${|}'' */",
      '\''
    )

  def testItalicTag(): Unit = {
    val before = s"/** ''some text${|}'' */"
    val after1 = s"/** ''some text'${|}' */"
    val after2 = s"/** ''some text''${|} */"

    checkGeneratedTextAfterTyping(before, after1, '\'')
    checkGeneratedTextAfterTyping(after1, after2, '\'')
  }

  def testAdvanceItalicToBold(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** ''${|}'' */",
      s"/** '''${|}''' */",
      '\''
    )
}
