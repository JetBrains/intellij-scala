package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaBlankLinesTest extends AbstractScalaFormatterTestBase {

  def testMinimumBlankLinesBeforePackageStatement_1(): Unit = {
    getCommonSettings.BLANK_LINES_BEFORE_PACKAGE = 0
    getCommonSettings.BLANK_LINES_AFTER_PACKAGE = 0

    val before =
      """// comment line
        |package A
        |// comment line
        |package B
        |// comment line
        |// comment line
        |package C
        |
        |import X
      """.stripMargin
    doTextTest(before)
  }


  def testMinimumBlankLinesBeforePackageStatement_2(): Unit = {
    getCommonSettings.BLANK_LINES_BEFORE_PACKAGE = 2
    getCommonSettings.BLANK_LINES_AFTER_PACKAGE = 0

    val before =
      """// comment line
        |package A
        |// comment line
        |package B
        |// comment line
        |// comment line
        |package C
        |
        |import X
      """.stripMargin
    val after =
      """// comment line
        |
        |
        |package A
        |// comment line
        |package B
        |// comment line
        |// comment line
        |package C
        |
        |import X
      """.stripMargin

    doTextTest(before, after)
  }

  def testMinimumBlankLinesBeforePackageStatement_3(): Unit = {
    getCommonSettings.BLANK_LINES_BEFORE_PACKAGE = 2
    getCommonSettings.BLANK_LINES_AFTER_PACKAGE = 0

    val before =
      """/* comment block */
        |package A
        |// comment line
        |package B
        |// comment line
        |// comment line
        |package C
        |
        |import X
      """.stripMargin
    val after =
      """/* comment block */
        |
        |
        |package A
        |// comment line
        |package B
        |// comment line
        |// comment line
        |package C
        |
        |import X
      """.stripMargin

    doTextTest(before, after)
  }

  def testMinimumBlankLinesAfterPackageStatement_1(): Unit = {
    getCommonSettings.BLANK_LINES_AFTER_PACKAGE = 0
    val before =
      """// comment line
        |package A
        |// comment line
        |package B
        |// comment line
        |// comment line
        |package C
        |class X()
      """.stripMargin
    doTextTest(before)
  }

  def testMinimumBlankLinesAfterPackageStatement_2(): Unit = {
    getCommonSettings.BLANK_LINES_AFTER_PACKAGE = 2
    val before =
      """// comment line
        |package A
        |
        |
        |// comment line
        |package B
        |
        |
        |// comment line
        |// comment line
        |package C
        |
        |
        |class X()
      """.stripMargin
    doTextTest(before)
  }

  def testMinimumBlankLinesBeforeImports_1(): Unit = {
    getCommonSettings.BLANK_LINES_BEFORE_IMPORTS = 0
    val before =
      """package A
        |package B
        |
        |// comment line
        |import X
        |// comment line
        |import Y
        |// comment line
        |// comment line
        |import Z
      """.stripMargin
    doTextTest(before)
  }

  def testMinimumBlankLinesBeforeImports_2(): Unit = {
    getCommonSettings.BLANK_LINES_BEFORE_IMPORTS = 2

    val before =
      """package A
        |package B
        |
        |// comment line
        |import X
        |// comment line
        |import Y
        |// comment line
        |// comment line
        |import Z
      """.stripMargin
    val after =
      """package A
        |package B
        |
        |// comment line
        |
        |
        |import X
        |// comment line
        |import Y
        |// comment line
        |// comment line
        |import Z
      """.stripMargin

    doTextTest(before, after)
  }

}