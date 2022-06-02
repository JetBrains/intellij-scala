package org.jetbrains.plugins.scala.codeInsight.intentions.imports.worksheet

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaWorksheetIntentionTestBase
import org.jetbrains.plugins.scala.codeInsight.intentions.imports.ImportStableMemberIntentionBaseTest

class ImportStableMemberInWorksheetIntentionTest extends ImportStableMemberIntentionBaseTest with ScalaWorksheetIntentionTestBase {

  def testParameterizedDef_TopLevelStatement(): Unit = doTest(
    "scala.Option.<caret>empty[Int]",
    """<caret>import scala.Option.empty
      |
      |empty[Int]""".stripMargin
  )
}
