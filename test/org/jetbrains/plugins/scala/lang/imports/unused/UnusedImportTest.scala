package org.jetbrains.plugins.scala.lang.imports.unused

import org.jetbrains.plugins.scala.base.AssertMatches

/**
  * Created by Svyatoslav Ilinskiy on 24.07.16.
  */
class UnusedImportTest extends UnusedImportTestBase with AssertMatches {
  def testTwoUnusedSelectorsOnSameLine(): Unit = {
    val text =
      """
        |import java.util.{Set, ArrayList}
        |
        |object Doo
      """.stripMargin
    assertMatches(messages(text)) {
      case HighlightMessage("import java.util.{Set, ArrayList}", _) :: Nil =>
    }
  }

}
