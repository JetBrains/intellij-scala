package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

abstract class AutoBraceTestBase extends EditorActionTestBase {

  /**************************************** Test in multiple contexts *************************************************/
  case class SubsequentConstructNewlineSeparator(separator: String)
  val NextConstructOnNewline = SubsequentConstructNewlineSeparator("\n")
  val NextConstructOnSameLine = SubsequentConstructNewlineSeparator(" ")

  val contexts = Seq(
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
      |if (cond)
      |""".stripMargin -> "else elseBranch",
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
    """
      |try
      |""".stripMargin -> "catch { e => something }",
    """
      |try
      |""".stripMargin -> "finally something",
  )

  def checkBackspaceInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                                  bodyAfter: (String, SubsequentConstructNewlineSeparator),
                                  bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                                  checkContextsWithPostfix: Boolean = true): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, checkContextsWithPostfix)(checkGeneratedTextAfterBackspace)

  def checkTypingInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                               bodyAfter: (String, SubsequentConstructNewlineSeparator),
                               bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                               typedChar: Char,
                               checkContextsWithPostfix: Boolean = true): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, checkContextsWithPostfix)(checkGeneratedTextAfterTyping(_, _, typedChar))

  def checkInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                         bodyAfter: (String, SubsequentConstructNewlineSeparator),
                         bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                         checkContextsWithPostfix: Boolean = true)
                        (check: (String, String) => Unit): Unit = {

    def transform(body: String): String =
      body.trim.replace("def test =", "")

    val settings = ScalaApplicationSettings.getInstance()

    for ((context, contextPostfix) <- contexts if checkContextsWithPostfix || contextPostfix.isEmpty) {
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
