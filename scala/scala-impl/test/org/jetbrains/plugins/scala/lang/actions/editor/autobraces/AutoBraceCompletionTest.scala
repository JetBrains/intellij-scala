package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.plugins.scala.editor.typedHandler.AutoBraceLookupListenerService
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaKeywordLookupItem.KeywordInsertHandler
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class AutoBraceCompletionTest extends ScalaCompletionTestBase {

  protected override def setUp(): Unit = {
    super.setUp()
    getProject.getService(classOf[AutoBraceLookupListenerService])
  }

  def testAutoBraceCompletionWithNewline(): Unit = doRawCompletionTest(
    s"""object Test {
       |  def ella(i: Int): Unit = ()
       |  if (true)
       |    4
       |    e$CARET
       |
       |  blub
       |}
       |""".stripMargin,
    s"""object Test {
       |  def ella(i: Int): Unit = ()
       |  if (true) {
       |    4
       |    ella($CARET)
       |  }
       |
       |  blub
       |}
       |""".stripMargin,
    '\n'
  )(_.getLookupString.contains("ella"))

  def testAutoBraceCompletionWithTab(): Unit = doRawCompletionTest(
    s"""object Test {
       |  def ella(i: Int): Unit = ()
       |  if (true)
       |    4
       |    e$CARET
       |
       |  blub
       |}
       |""".stripMargin,
    s"""object Test {
       |  def ella(i: Int): Unit = ()
       |  if (true) {
       |    4
       |    ella($CARET)
       |  }
       |
       |  blub
       |}
       |""".stripMargin,
    '\t'
  )(_.getLookupString.contains("ella"))

  def testAutoBraceCompletionWithContinuationKeyword(): Unit = doRawCompletionTest(
    s"""
       |object Test {
       |  if (true)
       |    4
       |    el$CARET
       |}
       |""".stripMargin,
    s"""
       |object Test {
       |  if (true)
       |    4
       |    else $CARET
       |}
       |""".stripMargin,
    '\t'
  ) {
    case item: LookupElementBuilder =>
      item.getInsertHandler.asOptionOf[KeywordInsertHandler].exists(_.keyword == "else")
    case _ => false
  }

  def testAutoBraceAbortionContinuationKeyword(): Unit = checkNonEmptyCompletionWithKeyAbortion(
    s"""object Test {
       |  def elsa(i: Int): Unit = ()
       |  if (true)
       |    4
       |    els$CARET
       |
       |  blub
       |}
       |""".stripMargin,
    s"""object Test {
       |  def elsa(i: Int): Unit = ()
       |  if (true)
       |    4
       |    else$CARET
       |
       |  blub
       |}
       |""".stripMargin,
    'e'
  )

  def testAutoBraceAbortionWithKey(): Unit = checkNonEmptyCompletionWithKeyAbortion(
    s"""
       |def ella(i: Int): Unit = ()
       |if (true)
       |  4
       |  e$CARET
       |
       |blub
       |""".stripMargin,
    s"""
       |def ella(i: Int): Unit = ()
       |if (true) {
       |  4
       |  eü$CARET
       |}
       |
       |blub
       |""".stripMargin,
    'ü'
  )

  def testAutoBraceAbortionAfterUncertainContinuation(): Unit = checkEmptyCompletionAbortion(
    s"""object Test {
       |  def ella(i: Int): Unit = ()
       |  if (true)
       |    4
       |    ex$CARET
       |
       |  blub
       |}
       |""".stripMargin,
    s"""object Test {
       |  def ella(i: Int): Unit = ()
       |  if (true) {
       |    4
       |    ex$CARET
       |  }
       |
       |  blub
       |}
       |""".stripMargin,
  )

  def testAutoBraceAbortionAfterPossibleContinuation(): Unit = checkEmptyCompletionAbortion(
    s"""object Test {
       |  def ella(i: Int): Unit = ()
       |  if (true)
       |    4
       |    el$CARET
       |
       |  blub
       |}
       |""".stripMargin,
    s"""object Test {
       |  def ella(i: Int): Unit = ()
       |  if (true)
       |    4
       |    el$CARET
       |
       |  blub
       |}
       |""".stripMargin,
  )

  // todo: fix completion from within
  /*def testAutoBraceCompletionWithNewlineWithin(): Unit = doRawCompletionTest(
    s"""
       |def ella(i: Int): Unit = ()
       |if (true)
       |  expr
       |  e${CARET}l
       |
       |blub
       |""".stripMargin,
    s"""
       |def ella(i: Int): Unit = ()
       |if (true) {
       |  expr
       |  ella(${CARET})l
       |}
       |
       |blub
       |""".stripMargin,
    '\n'
  )(_.getLookupString.contains("ella"))*/

  /*def testAutoBraceCompletionWithTabWithin(): Unit = doRawCompletionTest(
    s"""
       |def ella(i: Int): Unit = ()
       |if (true)
       |  expr
       |  e${CARET}l
       |
       |blub
       |""".stripMargin,
    s"""
       |def ella(i: Int): Unit = ()
       |if (true) {
       |  expr
       |  ella($CARET)
       |}
       |
       |blub
       |""".stripMargin,
    '\t'
  )(_.getLookupString.contains("ella"))*/
}
