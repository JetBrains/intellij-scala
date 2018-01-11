package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

import scala.collection.JavaConverters._

/**
  * @author Nikolay.Tropin
  */
abstract class BadCodeGreenTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import CodeInsightTestFixture.CARET_MARKER

  def doTest(text: String): Unit = {
    myFixture.configureByText("dummy.scala", text)
    val caretIndex = text.indexOf(CARET_MARKER)
    def isAroundCaret(info: HighlightInfo) = caretIndex == -1 || new TextRange(info.getStartOffset, info.getEndOffset).contains(caretIndex)
    val infos = myFixture.doHighlighting().asScala

    val warnings = infos.filter(i => StringUtil.isNotEmpty(i.getDescription) && isAroundCaret(i))

    if (shouldPass) {
      assert(warnings.nonEmpty, "No highlightings found")
    } else if (warnings.nonEmpty) {
      failingTestPassed()
    }
  }
}
