package org.jetbrains.plugins.scala.lang.formatter.tests

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.util.Markers

trait SelectionTest extends AbstractScalaFormatterTestBase with Markers {

  override def doTextTest(text: String, textAfter: String): Unit = {
    val inputWithMarkers = StringUtil.convertLineSeparators(text)
    val outputWithMarkers = StringUtil.convertLineSeparators(textAfter)
    val (inputWithoutMarkers, extractedTextRanges) = extractMarkers(inputWithMarkers)
    val (outputWithoutMarkers, _) = extractMarkers(outputWithMarkers)

    myTextRanges = extractedTextRanges

    super.doTextTest(inputWithoutMarkers, outputWithoutMarkers)
  }

}
