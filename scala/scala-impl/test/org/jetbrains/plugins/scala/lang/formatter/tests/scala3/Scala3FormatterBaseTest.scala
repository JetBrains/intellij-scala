package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

abstract class Scala3FormatterBaseTest extends AbstractScalaFormatterTestBase {
  override protected def language: Language = Scala3Language.INSTANCE

  protected def doTextTestWithExtraSpaces(before0: String): Unit =
    doTextTestWithExtraSpaces(before0, before0)

  protected def doTextTestWithExtraSpaces(before0: String, after0: String): Unit = {
    val before = before0.withNormalizedSeparator
    val after = after0.withNormalizedSeparator

    doTextTest(before, after)

    val textWithExtraSpaces = before.replaceAll("\\s", "$0 ") // insert space after any other space/new line
    doTextTest(textWithExtraSpaces, after)
  }

  protected def doForYieldDoTest(before: String): Unit =
    doForYieldDoTest(before, before)

  protected def doForYieldDoTest(before0: String, after0: String): Unit = {
    doTextTestWithExtraSpaces(before0, after0)

    val Yield = "yield"
    val Do = "do"
    val (before1, after1) =
      if (before0.contains(Yield)) (before0.replace(Yield, Do), after0.replace(Yield, Do))
      else (before0.replace(Do, Yield), after0.replace(Do, Yield))

    doTextTestWithExtraSpaces(before1, after1)
  }
}
