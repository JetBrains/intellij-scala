package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaBlankLinesTest_PackageAndImport extends AbstractScalaFormatterTestBase {

  def testBeforePackage_1(): Unit = {
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

  def testBeforePackage_2(): Unit = {
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

  def testBeforePackage_3(): Unit = {
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

  def testAfterPackage_1(): Unit = {
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

  def testAfterPackage_2(): Unit = {
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

  def testBeforeImports_1(): Unit = {
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

  def testBeforeImports_2(): Unit = {
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

  def testAfterImports_1(): Unit = {
    val before =
      """package A
        |import x.y.z
        |import a.b.c
        |class a
        |""".stripMargin

    val after0 =
      """package A
        |
        |import x.y.z
        |import a.b.c
        |class a
        |""".stripMargin

    val after1 =
      """package A
        |
        |import x.y.z
        |import a.b.c
        |
        |class a
        |""".stripMargin

    val after2 =
      """package A
        |
        |import x.y.z
        |import a.b.c
        |
        |
        |class a
        |""".stripMargin

    val after3 =
      """package A
        |
        |import x.y.z
        |import a.b.c
        |
        |
        |
        |class a
        |""".stripMargin

    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 0
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 0
    doTextTest(before, after0)

    getCommonSettings.BLANK_LINES_AROUND_CLASS = 0

    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 1
    doTextTest(before, after1)
    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 2
    doTextTest(before, after2)
    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 3
    doTextTest(before, after3)

    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 0

    // around class shouldn't affect first class, after imports, like in Java
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 1
    doTextTest(before, after0)
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 2
    doTextTest(before, after0)
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 3
    doTextTest(before, after0)
  }

  def testAfterImports_2(): Unit = {
    val before =
      """package A
        |import x.y.z
        |import a.b.c
        |//class
        |class a
        |""".stripMargin

    val after0 =
      """package A
        |
        |import x.y.z
        |import a.b.c
        |//class
        |class a
        |""".stripMargin

    val after1 =
      """package A
        |
        |import x.y.z
        |import a.b.c
        |
        |//class
        |class a
        |""".stripMargin

    val after2 =
      """package A
        |
        |import x.y.z
        |import a.b.c
        |
        |
        |//class
        |class a
        |""".stripMargin

    val after3 =
      """package A
        |
        |import x.y.z
        |import a.b.c
        |
        |
        |
        |//class
        |class a
        |""".stripMargin

    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 0
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 0
    doTextTest(before, after0)

    getCommonSettings.BLANK_LINES_AROUND_CLASS = 0

    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 1
    doTextTest(before, after1)
    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 2
    doTextTest(before, after2)
    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 3
    doTextTest(before, after3)

    getCommonSettings.BLANK_LINES_AFTER_IMPORTS = 0

    // around class shouldn't affect first class, after imports, like in Java
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 1
    doTextTest(before, after0)
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 2
    doTextTest(before, after0)
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 3
    doTextTest(before, after0)
  }
}