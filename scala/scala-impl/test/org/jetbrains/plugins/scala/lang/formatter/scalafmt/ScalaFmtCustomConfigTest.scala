package org.jetbrains.plugins.scala.lang.formatter.scalafmt

class ScalaFmtCustomConfigTest extends ScalaFmtTestBase {

  def testIncompleteFileWithRewriteRules_1(): Unit = {
    setScalafmtConfig("redundantBraces.conf")
    val before =
      s"""trait X {
         |     def foo = 42
         |}
         |def zoo(): Int = 42""".stripMargin
    val after =
      s"""trait X {
         |  def foo = 42
         |}
         |def zoo(): Int = 42""".stripMargin
    doTextTest(before, after)
  }

  def testIncompleteFileWithRewriteRules_2(): Unit = {
    setScalafmtConfig("redundantBraces.conf")
    val before =
      s"""trait X {
         |def foo = 42
         |}
         |def zoo(): Int = 42""".stripMargin
    val after =
      s"""trait X {
         |  def foo = 42
         |}
         |def zoo(): Int = 42""".stripMargin
    doTextTest(before, after)
  }

  def testRewriteRules_avoidInfix(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    val before =
      s"""class C {
         |  def foo = 1 to 42
         |}
         |""".stripMargin
    val after =
      s"""class C {
         |  def foo = 1.to(42)
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  def testRewriteRules_expandImport(): Unit = {
    setScalafmtConfig("expandImport.conf")
    val before =
      s"""import a.{
         |    b,
         |    c
         |  }, h.{
         |    k, l
         |  }
         |  import d.e.{f, g}
         |  import a.{
         |      foo => bar,
         |      zzzz => _,
         |      _
         |    }
         |class C {}
         |""".stripMargin
    val after =
      s"""import a.b
         |import a.c
         |import h.k
         |import h.l
         |import d.e.f
         |import d.e.g
         |import a.{foo => bar, zzzz => _, _}
         |class C {}
         |""".stripMargin
    doTextTest(before, after)
  }

  def testConfigWithIncludes(): Unit = {
    setScalafmtConfig("config_root.conf")
    doTextTest(
      """class A {
        |  def foo(parameterName: Int): Unit = {
        |    val x = 42
        |    val xxxxxxxx = 23
        |  }
        |}
        |""".stripMargin,
      """class A {
        |  def foo(
        |      parameterName: Int)
        |    : Unit = {
        |    val x        = 42
        |    val xxxxxxxx = 23
        |  }
        |}
        |""".stripMargin
    )
  }

  def testConfigWithIncludes_WithoutExtension(): Unit = {
    setScalafmtConfig("config_root_include_without_extension.conf")
    doTextTest(
      """class A {
        |  def foo(parameterName: Int): Unit = {
        |    val x = 42
        |    val xxxxxxxx = 23
        |  }
        |}
        |""".stripMargin,
      """class A {
        |  def foo(
        |      parameterName: Int)
        |    : Unit = {
        |    val x        = 42
        |    val xxxxxxxx = 23
        |  }
        |}
        |""".stripMargin
    )
  }

  def testRewriteRules_sortImports(): Unit = {
    setScalafmtConfig("sortImports.conf")
    val before =
      s"""import foo.{Zilch, bar, Random, sand}
         |""".stripMargin
    val after =
      s"""import foo.{bar, sand, Random, Zilch}
         |""".stripMargin
    doTextTest(before, after)
  }

  def testShouldNotFailToParseConfigWithAllParametersPresent_2_7_5(): Unit = {
    setScalafmtConfig("all_parameters_2_7_5.conf")
    doTextTest(
      raw"""class X {
           |  42
           |}
           |""".stripMargin,
    )
  }
}