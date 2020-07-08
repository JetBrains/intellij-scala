package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

abstract class AutoBraceTestBase extends EditorActionTestBase {
  val space = " "
  val indent = "  "

  case class SubsequentConstructNewlineSeparator(separator: String)
  val ContinuationOnNewline = SubsequentConstructNewlineSeparator("\n")
  val ContinuationOnSameLine = SubsequentConstructNewlineSeparator(" ")

  val uncontinuedContexts = Seq(
    """
      |def test =
      |""".stripMargin -> "",
    """
      |val test =
      |""".stripMargin -> "",
    """
      |if (cond)
      |""".stripMargin -> "",
    """
      |if (cond) thenBranch
      |else
      |""".stripMargin -> "",
    """
      |for (x <- xs)
      |""".stripMargin -> "",
    """
      |for (x <- xs) yield
      |""".stripMargin -> "",
    """
      |while (cond)
      |""".stripMargin -> "",
    """
      |try something
      |finally
      |""".stripMargin -> "",
  )

  val continuedContexts = Seq(
    """
      |if (cond)
      |""".stripMargin -> "else elseBranch",
    """
      |try
      |""".stripMargin -> "catch { e => something }",
    """
      |try
      |""".stripMargin -> "finally something",
  )

  val allContexts = uncontinuedContexts ++ continuedContexts

  def checkBackspaceInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                                  bodyAfter: (String, SubsequentConstructNewlineSeparator),
                                  bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator)): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, allContexts)(checkGeneratedTextAfterBackspace)

  def checkTypingInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                               bodyAfter: (String, SubsequentConstructNewlineSeparator),
                               bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                               typedChar: Char): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, allContexts)(checkGeneratedTextAfterTyping(_, _, typedChar))

  def checkBackspaceInContinuedContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                                          bodyAfter: (String, SubsequentConstructNewlineSeparator),
                                          bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator)): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, continuedContexts)(checkGeneratedTextAfterBackspace)

  def checkTypingInContinuedContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                                       bodyAfter: (String, SubsequentConstructNewlineSeparator),
                                       bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                                       typedChar: Char): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, continuedContexts)(checkGeneratedTextAfterTyping(_, _, typedChar))

  def checkBackspaceInUncontinuedContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                                          bodyAfter: (String, SubsequentConstructNewlineSeparator),
                                          bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator)): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, uncontinuedContexts)(checkGeneratedTextAfterBackspace)

  def checkTypingInUncontinuedContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                                       bodyAfter: (String, SubsequentConstructNewlineSeparator),
                                       bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                                       typedChar: Char): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, uncontinuedContexts)(checkGeneratedTextAfterTyping(_, _, typedChar))




  def checkInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                         bodyAfter: (String, SubsequentConstructNewlineSeparator),
                         bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                         contexts: Seq[(String, String)])
                        (check: (String, String) => Unit): Unit = {

    def transform(body: String): String =
      body.trim.replace("def test =", "")

    val settings = ScalaApplicationSettings.getInstance()

    for ((context, contextPostfix) <- contexts) {
      def buildBody(body: (String, SubsequentConstructNewlineSeparator)): String = {
        val (text, sep) = body

        val postfix = {
          if (contextPostfix.isEmpty) "\n"
          else sep.separator + contextPostfix
        }

        context.trim + transform(text) + postfix
      }

      val before = buildBody(bodyBefore)
      assert(settings.HANDLE_BLOCK_BRACES_AUTOMATICALLY)

      try {
        check(before, buildBody(bodyAfter))
        settings.HANDLE_BLOCK_BRACES_AUTOMATICALLY = false
        check(before, buildBody(bodyAfterWithSettingsTurnedOff))
      } finally {
        settings.HANDLE_BLOCK_BRACES_AUTOMATICALLY = true
      }
    }
  }
}
