package org.jetbrains.plugins.scala.lang.formatter.scalafmt

import org.jetbrains.plugins.scala.util.Markers

class ScalaFmtCustomConfigSelectionTest extends ScalaFmtSelectionTestBase with Markers {

  def testScl14129_avoidInfix(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    val before =
      s"""
         |class C {
         |  ${startMarker}def foo = 1 to 42$endMarker
         |}
       """.stripMargin
    val after =
      s"""
         |class C {
         |  def foo = 1.to(42)
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testScl14129_avoidInfix_1(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    val before =
      s"""
         |class C {
         |  def foo = ${startMarker}1     ${endMarker}to   42
         |}
       """.stripMargin
    val after =
      s"""
         |class C {
         |  def foo = 1 to   42
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testScl14129_avoidInfix_2(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    val before =
      s"""
         |class C {
         |  def foo = ${startMarker}1     to  $endMarker 42
         |}
       """.stripMargin
    val after =
      s"""
         |class C {
         |  def foo = 1 to 42
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testScl14129_avoidInfix_3(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    val before =
      s"""
         |class C {
         |  def foo = ${startMarker}1     to   42$endMarker
         |}
       """.stripMargin
    val after =
      s"""
         |class C {
         |  def foo = 1.to(42)
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testScl14129_avoidInfix_4(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    val before =
      s"""
         |class C {
         |  ${startMarker}def foo  =   1     to   42$endMarker
         |}
       """.stripMargin
    val after =
      s"""
         |class C {
         |  ${startMarker}def foo = 1.to(42)$endMarker
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testTopLevelFunctionWithInfixRewrite(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(
      """def foo = {
        |  List(1, 2, 3).sum
        |}
        |""".stripMargin
    )
  }

  def testTopFunctionWithInfixRewrite(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(
      """class X {
        |  def foo = {
        |    List(1, 2, 3).sum
        |  }
        |}
        |""".stripMargin
    )
  }

  def testScl14129_spacesAroundRewrite(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(
      s"""class C {
         |  def foo =  $startMarker  (    1      to      42   )   +      $endMarker 11
         |}
         |""".stripMargin,
      s"""class C {
         |  def foo = (1 to 42) + 11
         |}
         |""".stripMargin
    )
  }

  def testScl14129_spacesAroundRewrite_1(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(
      s"""class C {
         |  def foo = $startMarker   (    1      to      42   )   +       11 $endMarker
         |}
         |""".stripMargin,
      s"""class C {
         |  def foo = $startMarker(1.to(42)) + 11 $endMarker
         |}
         |""".stripMargin
    )
  }

  def testScl14129_spacesAroundRewrite_AllRangesNoExceptions(): Unit = {
    setScalafmtConfig("avoidInfix.conf")
    doAllRangesTextTest(
      s"""class C {
         |  def foo =    (    1      to      42   )   +       11
         |}
         |""".stripMargin,
      checkResult = false
    )
  }

  def testInfixInParenthesis(): Unit = {
    val before =
      s"""class C {
         |  (${start}1$end + 2)
         |}
         |""".stripMargin
    val after =
      s"""class C {
         |  (${start}1$end + 2)
         |}
         |""".stripMargin
    doTextTest(before, after)
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(before, after)
  }

  def testInfixInParenthesis_1(): Unit = {
    val before =
      s"""class C {
         |  (${start}1*2$end + 2)
         |}
         |""".stripMargin
    val after =
      s"""class C {
         |  (${start}1 * 2$end + 2)
         |}
         |""".stripMargin
    doTextTest(before, after)
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(before, after)
  }

  def testInfixInParenthesis_2(): Unit = {
    val before =
      s"""class C {
         |  (0 + ${start}1*2$end)
         |}
         |""".stripMargin
    val after =
      s"""class C {
         |  (0 + ${start}1 * 2$end)
         |}
         |""".stripMargin
    doTextTest(before, after)
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(before, after)
  }

  def testInfixInParenthesis_3(): Unit = {
    val before =
      s"""class C {
         |  (0 + ${start}1*2$end + 3)
         |}
         |""".stripMargin
    val after =
      s"""class C {
         |  (0 + ${start}1 * 2$end + 3)
         |}
         |""".stripMargin
    doTextTest(before, after)
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(before, after)
  }

  def testInfixInParenthesis_4(): Unit = {
    val before =
      s"""class C {
         |  ($start(foo() + bar())$end + 2)
         |}
         |""".stripMargin
    val after =
      s"""class C {
         |  ($start(foo() + bar())$end + 2)
         |}
         |""".stripMargin
    doTextTest(before, after)
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(before, after)
  }

  def testInfixInParenthesis_5(): Unit = {
    val before =
      s"""class C {
         |  ($start{ foo() + bar() }$end + 2)
         |}
         |""".stripMargin
    val after =
      s"""class C {
         |  ($start{ foo() + bar() }$end + 2)
         |}
         |""".stripMargin
    doTextTest(before, after)
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(before, after)
  }

  def testInfixInParenthesis_6(): Unit = {
    val before =
      s"""class C {
         |  (${start}id$end + 2)
         |}
         |""".stripMargin
    val after =
      s"""class C {
         |  (${start}id$end + 2)
         |}
         |""".stripMargin
    doTextTest(before, after)
    setScalafmtConfig("avoidInfix.conf")
    doTextTest(before, after)
  }

  def testMethodCallInParenthesis(): Unit =
    doAllRangesTextTest(
      s"""class C {
         |  (obj.method(42).method(23))
         |}
         |""".stripMargin
    )

  def testMethodCallInBraces(): Unit =
    doTextTest(
      s"""class C {
         |  {${start}obj${end}.method(42).method(23)}
         |}
         |""".stripMargin,
      s"""class C {
         |  { ${start}obj${end}.method(42).method(23)}
         |}
         |""".stripMargin
    )

  def testSCL14129_expandImport(): Unit = {
    setScalafmtConfig("expandImport.conf")
    val before =
      s"""
         |${startMarker}import a.{
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
         |    }$endMarker
         |class C {}
       """.stripMargin
    val after =
      s"""
         |import h.l
         |import h.k
         |import a.c
         |import a.b
         |import d.e.g
         |import d.e.f
         |import a.{foo => bar, zzzz => _, _}
         |class C {}
       """.stripMargin
    doTextTest(before, after)
  }

  def testSCL14129_redundantBraces(): Unit = {
    setScalafmtConfig("redundantBraces.conf")
    val before =
      s"""
         |val myString = ${startMarker}s"prefix$${myHello}"$endMarker
       """.stripMargin
    val after =
      s"""
         |val myString = s"prefix$$myHello"
       """.stripMargin
    doTextTest(before, after)
  }

  def testScl14129_redundantBraces_1(): Unit = {
    setScalafmtConfig("redundantBraces.conf")
    val before =
      s"""
         |val myString = s"pre${startMarker}fix$${myHello}$endMarker"
       """.stripMargin
    val after =
      s"""
         |val myString = s"prefix$${myHello}"
       """.stripMargin
    doTextTest(before, after)
  }

  def testScl14129_redundantBraces_2(): Unit = {
    setScalafmtConfig("redundantBraces.conf")
    val before =
      s"""
         |def foo $startMarker= {
         |  List(1, 2, 3).sum
         |}$endMarker
         |""".stripMargin
    val after =
      s"""
         |def foo $startMarker= {
         |  List(1, 2, 3).sum
         |}$endMarker
         |""".stripMargin
    doTextTest(before, after)
  }

  def testScl14129_redundantBraces_3(): Unit = {
    setScalafmtConfig("redundantBraces.conf")
    val before =
      s"""
         |${startMarker}def foo = {
         |  List(1, 2, 3).sum
         |}$endMarker
         |""".stripMargin
    val after =
      s"""
         |def foo =
         |  List(1, 2, 3).sum
         |""".stripMargin
    doTextTest(before, after)
  }

  def testScl14129_redundantBraces_4(): Unit = {
    setScalafmtConfig("redundantBraces.conf")
    val before =
      s"""def foo = {
         |  List(1, 2, 3).sum
         |}
         |
         |class X {
         |  def foo = {
         |    List(1, 2, 3).sum
         |  }
         |
         |  class Y {
         |    def foo = {
         |      List(1, 2, 3).sum
         |    }
         |  }
         |}
         |""".stripMargin
    val after =
      s"""def foo =
         |  List(1, 2, 3).sum
         |
         |class X {
         |  def foo =
         |    List(1, 2, 3).sum
         |
         |  class Y {
         |    def foo =
         |      List(1, 2, 3).sum
         |  }
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  def testScl14129_redundantBraces_5(): Unit = {
    setScalafmtConfig("redundantBraces.conf")
    val before =
      s"""class X {
         |  def foo = {
         |    List(1, 2, 3).sum
         |  }
         |
         |  class Y {
         |    def foo = {
         |      List(1, 2, 3).sum
         |    }
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class X {
         |  def foo =
         |    List(1, 2, 3).sum
         |
         |  class Y {
         |    def foo =
         |      List(1, 2, 3).sum
         |  }
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  def testScl14129_sortImports(): Unit = {
    setScalafmtConfig("sortImports.conf")
    val before =
      s"""
         |import foo.{Zilch,$startMarker bar, Random, ${endMarker}sand}
       """.stripMargin
    val after =
      s"""
         |import foo.{Zilch, bar, Random, sand}
       """.stripMargin
    doTextTest(before, after)
  }

  def testFormatValMultilineDefinition_IfRewriteRulesExist_1(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    val before =
      s"""object Outer {
         |${startMarker}val x =
         |2 + 2$endMarker
         |}""".stripMargin
    val after =
      s"""object Outer {
         |  val x =
         |    2 + 2
         |}""".stripMargin
    doTextTest(before, after)
  }

  def testFormatValMultilineDefinition_IfRewriteRulesExist_2(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    val before =
      s"""object Outer {
         |$startMarker     val x =
         |2+2$endMarker
         |}""".stripMargin
    val after =
      s"""object Outer {
         |  val x =
         |    2 + 2
         |}""".stripMargin
    doTextTest(before, after)
  }

  def testFormatValMultilineDefinition_IfRewriteRulesExist_3(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    val before =
      s"""object Outer {
         |$startMarker     val x =
         |    2 + 2$endMarker
         |}""".stripMargin
    val after =
      s"""object Outer {
         |  val x =
         |    2 + 2
         |}""".stripMargin
    doTextTest(before, after)
  }

  def testTypeDefinitionFormat_IfRewriteRulesExist_1(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    val before =
      s"""
         |${startMarker}object Outer {
         |    val x=2+2
         |}$endMarker
         |""".stripMargin
    val after =
      s"""
         |object Outer {
         |  val x = 2 + 2
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  def testTypeDefinitionFormat_IfRewriteRulesExist_2(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    // FIXME: when object goes at top level in the beginning of the file, the leading space should be removed
    val before =
      s"""$startMarker  object Outer {
         |val x=2+2
         |  }$endMarker
         |""".stripMargin
    val after =
      s"""$startMarker  object Outer {
         |  val x = 2 + 2
         |}$endMarker
         |""".stripMargin
    doTextTest(before, after)
  }

  def testTypeDefinitionFormat_IfRewriteRulesExist_3(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    val before =
      s"""
         |$startMarker  object Outer {
         |val x=2+2
         |  }$endMarker
         |""".stripMargin
    val after =
      s"""
         |$startMarker  object Outer {
         |  val x = 2 + 2
         |}$endMarker
         |""".stripMargin
    doTextTest(before, after)
  }


  def testTypeDefinitionFormat_IfRewriteRulesExist_4(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    val before =
      s"""class A
         |
         |$startMarker  object Outer {
         |val x=2+2
         |  }$endMarker
         |""".stripMargin
    val after =
      s"""class A
         |
         |object Outer {
         |  val x = 2 + 2
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  def testDeeplyNestedMethodDefinition_IfRewriteRulesExist(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    val before =
      s"""object Outer {
         |  object Inner {
         |${startMarker}def   bar  (args:Array[String]):Unit={
         |val x = 42
         |}$endMarker
         |  }
         |}""".stripMargin
    val after =
      s"""object Outer {
         |  object Inner {
         |    def bar(args: Array[String]): Unit = {
         |      val x = 42
         |    }
         |  }
         |}""".stripMargin
    doTextTest(before, after)
  }

  def testDeeplyNestedValMultilineDefinition_IfRewriteRulesExist(): Unit = {
    setScalafmtConfig("redundantBraces.conf")

    val before =
      s"""object Outer {
         |  object Inner {
         |${startMarker}val x =
         |2+2$endMarker
         |  }
         |}""".stripMargin
    val after =
      s"""object Outer {
         |  object Inner {
         |    val x =
         |      2 + 2
         |  }
         |}""".stripMargin
    doTextTest(before, after)
  }

  def test_EA244817_WithRewriteRules_2_7_5(): Unit = {
    setScalafmtConfig("EA-244817_2_7_5.conf")
    doTextTest(
      raw"""class X {
           |${start}val foo = 1 +
           |2 -
           |2 *
           |3 \
           |4$end
           |}
           |""".stripMargin,
      raw"""class X {
           |  ${start}val foo = 1 +
           |    2 -
           |    2 *
           |    3 \
           |    4$end
           |}
           |""".stripMargin
    )
  }

  def test_EA244817_WithRewriteRules_2_5_3(): Unit = {
    setScalafmtConfig("EA-244817_2_5_3.conf")
    // NOTE: 2.5.3 doesn't support unindentTopLevelOperators, but we ensure it doesn't fail
    doTextTest(
      raw"""class X {
           |${start}val foo = 1 +
           |2 -
           |2 *
           |3 \
           |4$end
           |}
           |""".stripMargin,
      raw"""class X {
           |  ${start}val foo = 1 +
           |        2 -
           |        2 *
           |            3 \
           |                4$end
           |}
           |""".stripMargin
    )
  }

  def testShouldNotFailToParseConfigWithAllParametersPresent_WithSelectionFormat_2_7_5(): Unit = {
    setScalafmtConfig("all_parameters_2_7_5.conf")
    doTextTest(
      raw"""class X {
           |  ${start}42$end
           |}
           |""".stripMargin,
    )
  }

  def testWithDocStringsOneLine_Unfold(): Unit = {
    setScalafmtConfig("docstrings_oneline_unfold_2_7_5.conf")
    doTextTest(
      s"""class A {
         |  ${start}2 + 2$end
         |}""".stripMargin
    )
  }
}