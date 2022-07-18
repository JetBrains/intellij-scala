package org.jetbrains.plugins.scala
package lang.actions.editor.autobraces

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.TestIndentUtils
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
abstract class AutoBraceTestBase extends EditorActionTestBase {
  //helper variables used to insert trailing spaces, otherwise they will be removed from the code by IntelliJ
  val space = " "
  val indent = "  "

  protected val InjectedCodePlaceholder = "__InjectedCodePlaceholder__"
  protected val ContinuationPlaceholder = "__ContinuationPlaceholder__"

  def injectCodeWithIndentAdjust(injectedCode: String, contextCode: String): String =
    TestIndentUtils.injectCodeWithIndentAdjust(injectedCode, contextCode, InjectedCodePlaceholder)

  protected case class BodyContext(text: String, withContinuation: Boolean) {
    if (withContinuation) {
      assert(text.contains(ContinuationPlaceholder))
    }

    def textWithoutContinuationPlaceholder: String = text.replace(ContinuationPlaceholder, "")
  }

  protected val uncontinuedContexts: Seq[BodyContext] = Seq(
    s"""
       |def test =$InjectedCodePlaceholder
       |""".stripMargin,
    s"""
       |val test =$InjectedCodePlaceholder
       |""".stripMargin,
    s"""
       |if (cond)$InjectedCodePlaceholder
       |""".stripMargin,
    s"""
       |if (cond) thenBranch
       |else$InjectedCodePlaceholder
       |""".stripMargin,
    s"""
       |for (x <- xs)$InjectedCodePlaceholder
       |""".stripMargin,
    s"""
       |for (x <- xs) yield$InjectedCodePlaceholder
       |""".stripMargin,
    s"""
       |while (cond)$InjectedCodePlaceholder
       |""".stripMargin,
    s"""
       |try something
       |finally$InjectedCodePlaceholder
       |""".stripMargin,

    // Same, but without new line in the end
    s"""
       |def test =$InjectedCodePlaceholder""".stripMargin,
  ).map(_.withNormalizedSeparator).map(BodyContext(_, withContinuation = false))

  protected val continuedContexts: Seq[BodyContext] = Seq(
    s"""
      |if (cond)$InjectedCodePlaceholder
      |${ContinuationPlaceholder}else elseBranch
      |""".stripMargin,
    s"""
      |try$InjectedCodePlaceholder
      |${ContinuationPlaceholder}catch { e => something }
      |""".stripMargin,
    s"""
      |try$InjectedCodePlaceholder
      |${ContinuationPlaceholder}finally something
      |""".stripMargin,
  ).map(_.withNormalizedSeparator).map(BodyContext(_, withContinuation = true))

  protected val allContexts: Seq[BodyContext] =
    uncontinuedContexts ++ continuedContexts

  private lazy val settings = ScalaApplicationSettings.getInstance()

  protected def checkInAllContexts(
    bodyBefore: String,
    bodyAfter: String,
    bodyAfterWithSettingsTurnedOff: String,
    contexts: Seq[BodyContext],
    check: (String, String) => Unit
  ): Unit = {
    contexts.foreach(checkInContext(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, _, check))
  }

  protected def checkInContext(
    bodyBefore: String,
    bodyAfter: String,
    bodyAfterWithSettingsTurnedOff: String,
    context: BodyContext,
    check: (String, String) => Unit,
  ): Unit = {
    def bodyInContext(body: String): String =
      injectCodeWithIndentAdjust(body, context.textWithoutContinuationPlaceholder)

    val before = bodyInContext(bodyBefore)
    val after = bodyInContext(bodyAfter)
    val afterWithSettingsOff = bodyInContext(bodyAfterWithSettingsTurnedOff)

    checkWithSettingsOnAndOf(before, after, afterWithSettingsOff, check)
  }

  protected def checkWithSettingsOnAndOf(
    before: String,
    after: String,
    afterWithSettingsOff: String,
    check: (String, String) => Unit,
  ): Unit = {

    assert(!settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY)
    assert(settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY)

    try {
      settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = true
      check(before, after)
      settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = false
      settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = false
      check(before, afterWithSettingsOff)
    } finally {
      settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = false
      settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = true
    }
  }
}
