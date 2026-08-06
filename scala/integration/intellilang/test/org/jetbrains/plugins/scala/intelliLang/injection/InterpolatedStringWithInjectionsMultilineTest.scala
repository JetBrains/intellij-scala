package org.jetbrains.plugins.scala.intelliLang.injection

import org.jetbrains.plugins.scala.intelliLang.injection.InjectionTestUtils.RegexpLangId
import org.jetbrains.plugins.scala.intelliLang.injection.ScalaInjectionTestFixture.ShredInfo
import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

class InterpolatedStringWithInjectionsMultilineTest extends InjectionViaCommentTestBase {
  def testRawInterpolatorWithEscapeSequences(): Unit = {
    doInjectedViaCommentTest(
      RegexpLangId,
      raw"""//language=RegExp
           |raw'''\w
           |     |\w\w
           |     |\w $$foo \w
           |     |$$foo \w $$foo
           |     |\w'''.stripMargin""".stripMargin,
      """\w
        |\w\w
        |\w InjectionPlaceholder \w
        |InjectionPlaceholder \w InjectionPlaceholder
        |\w""".stripMargin,
      Seq(
        ShredInfo((0, 3), (6, 9)),
        ShredInfo((3, 8), (9, 14)),
        ShredInfo((8, 11), (14, 17)),
        ShredInfo((11, 35), (21, 25), "InjectionPlaceholder"),
        ShredInfo((35, 35), (26, 26)),
        ShredInfo((35, 59), (29, 33), "InjectionPlaceholder"),
        ShredInfo((59, 80), (37, 38), "InjectionPlaceholder"),
        ShredInfo((80, 82), (38, 40)),
      )
    )
  }
}
