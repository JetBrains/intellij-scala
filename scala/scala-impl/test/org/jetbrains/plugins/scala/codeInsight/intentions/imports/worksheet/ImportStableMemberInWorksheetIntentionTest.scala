package org.jetbrains.plugins.scala.codeInsight.intentions.imports.worksheet

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaWorksheetIntentionTestBase
import org.jetbrains.plugins.scala.codeInsight.intentions.imports.ImportStableMemberIntentionTest

class ImportStableMemberInWorksheetIntentionTest extends ImportStableMemberIntentionTest with ScalaWorksheetIntentionTestBase {

  def testParameterizedDef_TopLevelStatement(): Unit = doTest(
    """scala.Option.<caret>empty[Int]
      |""".stripMargin,
    """import scala.Option.empty
      |
      |<caret>empty[Int]"""
      .stripMargin
  )
}
