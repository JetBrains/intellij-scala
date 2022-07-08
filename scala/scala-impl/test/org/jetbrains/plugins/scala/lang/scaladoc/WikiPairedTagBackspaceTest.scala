package org.jetbrains.plugins.scala.lang.scaladoc

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class WikiPairedTagBackspaceTest extends EditorActionTestBase {

  def testDeleteUnderlinedTag(): Unit =
    checkGeneratedTextAfterBackspace(
      s"/** __${|}blah blah__ */",
      "/** _blah blah */"
    )

  def testDeleteMonospaceTag(): Unit =
    checkGeneratedTextAfterBackspace(
      s"/** `${|}blahblah` */",
      "/** blahblah */"
    )

  def testDeleteItalicTag(): Unit =
    checkGeneratedTextAfterBackspace(
      s"/** ''${|}blah blah'' */",
      "/** 'blah blah */"
    )

  def testDeleteBoldTag(): Unit =
    checkGeneratedTextAfterBackspace(
      s"/** '''${|}blah blah''' */",
      "/** ''blah blah'' */"
    )

  def testDeleteSubscriptTag(): Unit =
    checkGeneratedTextAfterBackspace(
      s"/** ,,${|}blah blah,, */",
      "/** ,blah blah */"
    )

  def testScl6717(): Unit =
    checkGeneratedTextAfterBackspace(
      s"""
         | /**
         |  * a =${|} b
         |  */
       """.stripMargin,
      s"""
         | /**
         |  * a ${|} b
         |  */
       """.stripMargin
    )

  def testDeleteInnerCodeTag(): Unit =
    checkGeneratedTextAfterBackspace(
      s"""
         | /**
         |   * {{{${|}
         |   *   class A {
         |   *     def f (): Unit = {}
         |   * }
         |   *}}}
         |   */
      """.stripMargin,
      """
        | /**
        |   * {{
        |   *   class A {
        |   *     def f (): Unit = {}
        |   * }
        |   *
        |   */
      """.stripMargin
    )

  def testDeleteCodeLinkTag(): Unit =
    checkGeneratedTextAfterBackspace(
      s"/** [[${|}java.lang.String]] */",
      "/** [java.lang.String */"
    )

  def testDeleteEmptyItalicTag(): Unit =
    checkGeneratedTextAfterBackspace(
      s"/** ''${|}'' */",
      "/** ' */"
    )
}
