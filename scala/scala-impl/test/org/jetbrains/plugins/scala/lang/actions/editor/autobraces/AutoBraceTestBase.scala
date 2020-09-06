package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

abstract class AutoBraceTestBase extends EditorActionTestBase {
  val space = " "
  val indent = "  "

  case class ContinuationNewlineSeparator(separator: String)
  val ContinuationOnNewline = ContinuationNewlineSeparator("\n")
  val ContinuationOnSameLine = ContinuationNewlineSeparator(" ")

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

  def checkBackspaceInAllContexts(bodyBefore: (String, ContinuationNewlineSeparator),
                                  bodyAfter: (String, ContinuationNewlineSeparator),
                                  bodyAfterWithSettingsTurnedOff: (String, ContinuationNewlineSeparator)): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, allContexts, checkGeneratedTextAfterBackspace)

  def checkTypingInAllContexts(bodyBefore: (String, ContinuationNewlineSeparator),
                               bodyAfter: (String, ContinuationNewlineSeparator),
                               bodyAfterWithSettingsTurnedOff: (String, ContinuationNewlineSeparator),
                               typedChar: Char): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, allContexts, checkGeneratedTextAfterTyping(_, _, typedChar))

  val continuedContextsPreviewStrings = Seq("try", " finally ()", "finally ()")

  def checkBackspaceInContinuedContexts(bodyBefore: (String, ContinuationNewlineSeparator),
                                        bodyAfter: (String, ContinuationNewlineSeparator),
                                        bodyAfterWithSettingsTurnedOff: (String, ContinuationNewlineSeparator)): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, continuedContexts, checkGeneratedTextAfterBackspace, continuedContextsPreviewStrings)

  def checkTypingInContinuedContexts(bodyBefore: (String, ContinuationNewlineSeparator),
                                     bodyAfter: (String, ContinuationNewlineSeparator),
                                     bodyAfterWithSettingsTurnedOff: (String, ContinuationNewlineSeparator),
                                     typedChar: Char): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, continuedContexts, checkGeneratedTextAfterTyping(_, _, typedChar), continuedContextsPreviewStrings)

  def checkBackspaceInUncontinuedContexts(bodyBefore: (String, ContinuationNewlineSeparator),
                                          bodyAfter: (String, ContinuationNewlineSeparator),
                                          bodyAfterWithSettingsTurnedOff: (String, ContinuationNewlineSeparator)): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, uncontinuedContexts, checkGeneratedTextAfterBackspace)

  def checkTypingInUncontinuedContexts(bodyBefore: (String, ContinuationNewlineSeparator),
                                       bodyAfter: (String, ContinuationNewlineSeparator),
                                       bodyAfterWithSettingsTurnedOff: (String, ContinuationNewlineSeparator),
                                       typedChar: Char): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, uncontinuedContexts, checkGeneratedTextAfterTyping(_, _, typedChar))




  def checkInAllContexts(bodyBefore: (String, ContinuationNewlineSeparator),
                         bodyAfter: (String, ContinuationNewlineSeparator),
                         bodyAfterWithSettingsTurnedOff: (String, ContinuationNewlineSeparator),
                         contexts: Seq[(String, String)],
                         check: (String, String) => Unit,
                         removePreviewString: Seq[String] = Seq("def test =")): Unit = {

    def transform(body: String): String =
      removePreviewString.foldLeft(body.trim)(_.replace(_, ""))

    val settings = ScalaApplicationSettings.getInstance()

    for ((context, contextContinuation) <- contexts) {
      def buildBody(body: (String, ContinuationNewlineSeparator)): String = {
        val (text, sep) = body

        val postfix = {
          if (contextContinuation.isEmpty) "\n"
          else sep.separator + contextContinuation
        }

        context.trim + transform(text) + postfix
      }

      assert(!settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY)
      assert(settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY)

      val before = buildBody(bodyBefore)
      try {
        settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = true
        check(before, buildBody(bodyAfter))
        settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = false
        settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = false
        check(before, buildBody(bodyAfterWithSettingsTurnedOff))
      } finally {
        settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = false
        settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = true
      }
    }
  }
}
