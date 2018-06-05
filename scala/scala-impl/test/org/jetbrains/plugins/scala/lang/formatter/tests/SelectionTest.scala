package org.jetbrains.plugins.scala.lang.formatter.tests

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

trait SelectionTest extends AbstractScalaFormatterTestBase {
  val startMarker = "/*start*/"
  val endMarker = "/*end*/"

  override def doTextTest(text: String, textAfter: String): Unit = {
    myTextRange = null
    var input = StringUtil.convertLineSeparators(text)
    if (input.contains(startMarker) && input.contains(endMarker)) {
      val rangeStart = input.indexOf(startMarker)
      input = input.replace(startMarker, "")
      val rangeEnd = input.indexOf(endMarker)
      input = input.replace(endMarker, "")
      myTextRange = new TextRange(rangeStart, rangeEnd)
    }
    super.doTextTest(input, StringUtil.convertLineSeparators(textAfter))
  }

}
