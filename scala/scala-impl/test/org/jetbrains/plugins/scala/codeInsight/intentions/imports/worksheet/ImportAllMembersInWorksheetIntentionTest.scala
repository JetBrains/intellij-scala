package org.jetbrains.plugins.scala.codeInsight.intentions.imports.worksheet

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaWorksheetIntentionTestBase
import org.jetbrains.plugins.scala.codeInsight.intentions.imports.ImportAllMembersIntentionTest

class ImportAllMembersInWorksheetIntentionTest extends ImportAllMembersIntentionTest with ScalaWorksheetIntentionTestBase {

  def testWithExistedImport_TopLevelStatement(): Unit = doTest(
    """import math.E
      |
      |m<caret>ath.Pi
      |""".stripMargin,
    """import scala.math._
      |
      |<caret>Pi
      |""".stripMargin
  )
}
