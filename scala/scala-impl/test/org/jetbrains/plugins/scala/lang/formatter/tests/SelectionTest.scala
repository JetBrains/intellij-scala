package org.jetbrains.plugins.scala.lang.formatter.tests

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.util.Markers

import scala.collection.JavaConverters.seqAsJavaList

trait SelectionTest extends AbstractScalaFormatterTestBase with Markers {

  override def doTextTest(text: String, textAfter: String): Unit = {
    val inputWithMarkers = StringUtil.convertLineSeparators(text)
    val (inputWithoutMarkers, extractedTextRanges) = extractMarkers(inputWithMarkers)

    myTextRanges = seqAsJavaList(extractedTextRanges)

    super.doTextTest(inputWithoutMarkers, StringUtil.convertLineSeparators(textAfter))
  }

}
