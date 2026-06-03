package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import org.jetbrains.plugins.scala.lang.formatter.intellij.tests.LineCommentsTestOps

/**
 * See SCL-20780
 *
 * If you want some test examples to compile, add this to a separate file {{{
 *   export Foo.*
 *   object Foo {
 *     def foo0: Foo.type = ???
 *     def foo00(): Foo.type = ???
 *     def foo1(i: Int): Foo.type = ???
 *   }
 * }}}
 */
class Scala3FormatterMethodCallChainWithArgumentsWithColonSyntaxTest extends Scala3FormatterBaseTest with LineCommentsTestOps {

  def testExampleFromSCL20780_1(): Unit = doTextTestWithLineComments(
    """object Formatting:
      |  List(1).map {
      |    case x => x
      |  }
      |  .filter: x =>
      |    x > 0
      |end Formatting
      |""".stripMargin,
    """object Formatting:
      |  List(1).map {
      |      case x => x
      |    }
      |    .filter: x =>
      |      x > 0
      |end Formatting
      |""".stripMargin
  )

  def testExampleFromSCL20780_2(): Unit = doTextTestWithLineComments(
    """object Formatting:
      |  List(1).map:
      |    case x => x
      |  .filter: x =>
      |    x > 0
      |end Formatting
      |""".stripMargin
  )

  def testExampleFromSCL20780_3(): Unit = doTextTestWithLineComments(
    """val actual = List(id -> baseFill, sellOrder.id -> sellFill).foldM(tsPartFill): (ts, ordFillPr) =>
      |  val (id, (aq, qaq, t)) = ordFillPr
      |  ts.fillOrder(id, aq, qaq, t)
      |.toOption.get
      |""".stripMargin
  )

  def testOnlyColonSyntax(): Unit = doTextTestWithLineComments(
    """object example:
      |  foo1:
      |    42
      |
      |  foo0.foo1:
      |    42
      |
      |  foo00().foo1:
      |    42
      |
      |  foo00().foo1:
      |    42
      |
      |  foo1:
      |    42
      |  .foo1:
      |    42
      |  .foo1:
      |    42
      |  .foo0
      |
      |  foo0.foo1(42).foo1:
      |    42
      |  .foo0.foo1(42).foo1:
      |    42
      |  .foo0.foo1(42).foo1:
      |    42
      |  .foo0.foo1(42).foo0
      |end example
      |""".stripMargin
  )

  /** NOTE: see comment to a similar test case [[testOnlyBracesSyntax2]] */
  def testMixedColonSyntaxWithBracesSyntax_1(): Unit = doTextTestWithLineComments(
    """foo1 {
      |  42
      |}
      |  .foo1:
      |    42
      |  .foo1:
      |    42
      |""".stripMargin
  )

  def testMixedColonSyntaxWithBracesSyntax_2(): Unit = doTextTestWithLineComments(
    """foo1:
      |  42
      |.foo1 {
      |  42
      |}
      |.foo1:
      |  42
      |""".stripMargin
  )

  def testMixedColonSyntaxWithBracesSyntax_3(): Unit = doTextTestWithLineComments(
    """foo1:
      |  42
      |.foo1:
      |  42
      |.foo1 {
      |  42
      |}
       """.stripMargin
  )

  def testMixedColonSyntaxWithBracesSyntax_4(): Unit = doTextTestWithLineComments(
    """foo0.foo1 {
      |    42
      |  }
      |  .foo0.foo1:
      |    42
      |  .foo0.foo1:
      |    42
      |""".stripMargin
  )

  def testMixedColonSyntaxWithBracesSyntax_5(): Unit = doTextTestWithLineComments(
    """foo0.foo1:
      |  42
      |.foo0.foo1 {
      |  42
      |}
      |.foo0.foo1:
      |  42
      |""".stripMargin
  )

  def testMixedColonSyntaxWithBracesSyntax_6(): Unit = doTextTestWithLineComments(
    """foo0.foo1:
      |  42
      |.foo0.foo1:
      |  42
      |.foo0.foo1 {
      |  42
      |}
      |""".stripMargin
  )

  def testMixedColonSyntaxWithBracesSyntax_7(): Unit = doTextTestWithLineComments(
    """foo1(
      |  2 + 2
      |).foo0.foo1:
      |  3 + 3
      |""".stripMargin
  )

  def testOnlyBracesSyntax1(): Unit = doTextTestWithLineComments(
    """foo1 {
      |  42
      |}.foo1 {
      |  42
      |}
      |""".stripMargin
  )

  /**
   * NOTE: it would be nice if in this example block argument of first `foo1` call was indented,
   * the same way as it's indented in the next example when method chain starts with `foo0.foo1`
   * But I couldn't find a way to achieve this
   *
   * It's somehow related to "SmartIndent" (or ExpandableIndent) used in [[com.intellij.psi.formatter.java.LegacyChainedMethodCallsBlockBuilder]]
   * We can't simply apply same indent to the first block, it will add extra redundant indent to `foo1`
   *
   * Note, in Java they have a similar behaviour: {{{
   *   class Scratch {
   *       public static void main(String[] args) {
   *           foo(
   *           ) //notice this is not indented
   *                   .bar()
   *                   .bar();
   *
   *           foo().foo(
   *                   ) //notice this is indented
   *                   .bar()
   *                   .bar();
   *       }
   *   }
   * }}}
   */
  def testOnlyBracesSyntax2(): Unit = doTextTestWithLineComments(
    """foo1 {
      |  42
      |}
      |  .foo1 {
      |    42
      |  }
      |""".stripMargin
  )

  def testOnlyBracesSyntax3(): Unit = doTextTestWithLineComments(
    """foo0.foo1 {
      |    42
      |  }
      |  .foo1 {
      |    42
      |  }
      |""".stripMargin
  )

  def testOnlyBracesSyntax4(): Unit = doTextTestWithLineComments(
    """object example:
      |  foo0.foo1 {
      |    42
      |  }.foo1 {
      |    42
      |  }.foo1 {
      |    42
      |  }.foo0.foo1 {
      |    42
      |  }.foo0.foo1 {
      |    42
      |  }
      |end example""".stripMargin
  )

  def testOnlyBracesSyntax5(): Unit = doTextTestWithLineComments(
    """object example:
      |  foo0.foo1 {
      |      42
      |    }
      |    .foo0.foo1 {
      |      42
      |    }
      |    .foo0.foo1 {
      |      42
      |    }
      |end example""".stripMargin
  )

  def testOnlyColonSyntax_LambdaArg(): Unit = doTextTestWithLineComments(
    """List(1, 2, 3, 4).map:
      |  x => x
      |.filter(_ > 2)
      |
      |List(1, 2, 3, 4).map:
      |  x => x
      |.filter(_ > 2).map:
      |  x => x
      |.filter(_ > 2)
      |
      |List(1, 2, 3, 4).map:
      |  x => x
      |.map:
      |  x => x
      |.filter(_ > 2)
      |
      |List(1, 2, 3, 4)
      |  .map:
      |    x => x
      |  .map:
      |    x => x
      |  .filter(_ > 2)
      |
      |List(1, 2, 3, 4)
      |  .map:
      |    x => x
      |  .filter(_ > 2)
      |
      |List(1, 2, 3, 4)
      |  .map[Int]:
      |    x => x
      |  .filter(_ > 2)
      |""".stripMargin
  )

  def testOnlyColonSyntax_LambdaArgWithCase(): Unit = doTextTestWithLineComments(
    """List(1, 2, 3, 4).map:
      |  case x => x
      |.filter(_ > 2)
      |
      |List(1, 2, 3, 4).map:
      |  case x => x
      |.filter(_ > 2).map:
      |  case x => x
      |.filter(_ > 2)
      |
      |List(1, 2, 3, 4).map:
      |  case x => x
      |.map:
      |  case x => x
      |.filter(_ > 2)
      |
      |List(1, 2, 3, 4)
      |  .map:
      |    case x => x
      |  .map:
      |    case x => x
      |  .filter(_ > 2)
      |
      |List(1, 2, 3, 4)
      |  .map:
      |    case x => x
      |  .filter(_ > 2)
      |
      |List(1, 2, 3, 4)
      |  .map[Int]:
      |    case x => x
      |  .filter(_ > 2)
      |""".stripMargin
  )

  def testIndentFewerBraces(): Unit = {
    val text =
      """List(1, 2, 3, 4).map:
        |  case x => x
        |.filter(_ > 2)
        |
        |foo0.foo1(42).foo1:
        |  42
        |.foo0.foo1(42).foo1:
        |  42
        |.foo0.foo1(42).foo1:
        |  42
        |.foo0.foo1(42).foo0
        |
        |foo0.foo1:
        |  42
        |.foo0.foo1 {
        |  42
        |}
        |.foo0.foo1:
        |  42
        |""".stripMargin

    val afterWithExtraIndentForFewerBraces =
      """List(1, 2, 3, 4).map:
        |    case x => x
        |  .filter(_ > 2)
        |
        |foo0.foo1(42).foo1:
        |    42
        |  .foo0.foo1(42).foo1:
        |    42
        |  .foo0.foo1(42).foo1:
        |    42
        |  .foo0.foo1(42).foo0
        |
        |foo0.foo1:
        |    42
        |  .foo0.foo1 {
        |    42
        |  }
        |  .foo0.foo1:
        |    42
        |""".stripMargin

    doTextTestWithLineComments(text)
    scalaSettings.INDENT_FEWER_BRACES_IN_METHOD_CALL_CHAINS = true
    doTextTestWithLineComments(text, afterWithExtraIndentForFewerBraces)
  }

  //SCL-21539
  //Note: it's important to test "Colon" (:) separately because it affects the parsing order which matters in chains//
  //With `++` PSI structure is:
  //InfixExpression
  //  InfixExpression
  //  ReferenceExpression: ++
  //  MethodCall
  //
  //With `::` PSI structure is:
  //InfixExpression
  //  MethodCall
  //  ReferenceExpression: ::
  //  InfixExpression
  def testInfixExpressionWithFewerBraces_Colon(): Unit = {
    doTextTest(
      """class Foo(i: Int)(s: String)
        |
        |val fooList: List[Foo] = Foo(42):
        |  "hi"
        |:: Nil
        |
        |val fooList2: List[Foo] = Foo(42):
        |  "hi"
        |:: Foo(43):
        |  "ho"
        |:: Foo(44):
        |  "hut"
        |:: Nil
        |
        |// Without definition, just expression
        |Foo(42):
        |  "hi"
        |:: Nil
        |
        |Foo(42):
        |  "hi"
        |:: Foo(43):
        |  "ho"
        |:: Foo(44):
        |  "hut"
        |:: Nil
        |
        |// With single parameter clause
        |StringBuilder:
        |  "42"
        |:: StringBuilder:
        |  "42"
        |:: StringBuilder:
        |  "42"
        |:: Nil
        |""".stripMargin
    )
  }

  //SCL-21539
  def testInfixExpressionWithFewerBraces_Plus(): Unit = {
    doTextTest(
      """object Wrapper:
        |  StringBuilder:
        |    "42"
        |  ++ Nil
        |
        |  StringBuilder:
        |    "42"
        |  ++ StringBuilder:
        |    "42"
        |  ++ StringBuilder:
        |    "42"
        |  ++ StringBuilder:
        |    "42"""".stripMargin
    )
  }

  //SCL-21539
  def testInfixExpressionWithFewerBraces_Plus_MixedInfixAndDotChain(): Unit = {
    doTextTest(
      """object Wrapper:
        |  StringBuilder:
        |    "42"
        |  .++(StringBuilder:
        |    "42"
        |  )
        |  .++(StringBuilder:
        |    "42"
        |  )
        |    ++ StringBuilder:
        |    //NOTE: the line above is known to be poorly indented, but fixing this might be quite hard
        |    //and it's not clear if it worth it (do people even write such code?)
        |    "42"
        |  ++ StringBuilder:
        |    "42"
        |  .++(StringBuilder:
        |    "42"
        |  )
        |  .++(StringBuilder:
        |    "42"
        |  )
        |    ++ StringBuilder:
        |    //NOTE: the line above is known to be poorly indented, but fixing this might be quite hard
        |    //and it's not clear if it worth it (do people even write such code?)
        |    "42"
        |  ++ StringBuilder:
        |    "42"""".stripMargin
    )
  }

  //SCL-21539
  def testInfixExpressionWithFewerBraces_Lambdas(): Unit = {
    //TODO: the test fails, depends on SCL-22133
    return

    doTextTest(
      """class MyClass:
        |  Seq(1)
        |    ++ Seq(2)
        |    ++ Seq(3)
        |
        |  Seq(1)
        |    ++ Seq(2).map:
        |      case 2 => 2
        |    ++ Seq(3)
        |
        |  Seq(1)
        |    ++ Seq(2).map:
        |     x => x + 2
        |    ++ Seq(3)
        |
        |  Seq(1)
        |    ++ Seq(2).map:
        |     x =>
        |       x + 2
        |    ++ Seq(3)""".stripMargin
    )
  }

  //SCL-21539
  def testInfixExpressionWithFewerBraces_MixedColonAndPlus(): Unit = {
    doTextTest(
      """object Wrapper:
        |  StringBuilder:
        |    "1"
        |  ++ StringBuilder:
        |    "2"
        |  ++ StringBuilder:
        |    "3"
        |  :: StringBuilder:
        |    "11"
        |  :: StringBuilder:
        |    "22"
        |  :: StringBuilder:
        |    "33"
        |  :: Nil
        |    ++ StringBuilder:
        |    //NOTE: the line above is known to be poorly indented, but fixing this might be quite hard
        |    //and it's not clear if it worth it (do people even write such code?)
        |    "111"
        |  ++ StringBuilder:
        |    "222"
        |  ++ StringBuilder:
        |    "333"""".stripMargin
    )
  }

  def testMethodCallChain_MixedInfixAndDotNotation_MixedColonAndPlus_NoIndentationBasedSyntax(): Unit = {
    doTextTest(
      """object Wrapper:
        |  StringBuilder("1")
        |    ++ StringBuilder("2")
        |    ++ StringBuilder("3")
        |    ++ StringBuilder("4")
        |
        |  StringBuilder("1")
        |    ++ StringBuilder("2")
        |    ++ StringBuilder("3")
        |    :: StringBuilder("11")
        |    :: StringBuilder("22")
        |    :: StringBuilder("33")
        |    :: Nil
        |    ++ StringBuilder("111")
        |    ++ StringBuilder("222")
        |    ++ StringBuilder("333")
        |
        |  StringBuilder("1")
        |    .++(StringBuilder("2"))
        |    .++(StringBuilder("3"))
        |    ++ StringBuilder("4")
        |    ++ StringBuilder("5")
        |    .++(StringBuilder("6"))
        |    .++(StringBuilder("7"))
        |    ++ StringBuilder("8")
        |    ++ StringBuilder("9")
        |""".stripMargin
    )
  }

  def testMatchWithDotsWithBraces_OnlyMatchExpressions(): Unit = doTextTestWithLineComments(
    """object wrapper:
      |  foo0.match {
      |    case _ => 1
      |  }.match {
      |    case _ => 1
      |  }
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }.match {
      |      case _ => 1
      |    }
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }
      |    .match {
      |      case _ => 1
      |    }
      |""".stripMargin
  )

  def testMatchWithDotsWithBraces_MatchExpressionsWithMethodCalls(): Unit = doTextTestWithLineComments(
    """object wrapper:
      |  foo0.match {
      |    case _ => 1
      |  }.toString
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |    .match {
      |      case _ => 1
      |    }
      |
      |  foo0.toString
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |    .match {
      |      case _ => 1
      |    }
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |""".stripMargin
  )

  def testMatchWithDotsWithoutBraces_OnlyMatchExpressions(): Unit = doTextTestWithLineComments(
    """object wrapper:
      |  foo0.match
      |    case _ => 1
      |  .match
      |    case _ => 1
      |
      |  foo0
      |    .match
      |      case _ => 1
      |    .match
      |      case _ =>
      |        ??? match
      |          case 1 => 11
      |""".stripMargin
  )

  def testMatchWithDotsWithoutBraces_MatchExpressionsWithMethodCalls(): Unit = doTextTestWithLineComments(
    """object wrapper:
      |  foo0.match
      |    case _ => 1
      |  .toString
      |
      |  foo0
      |    .match
      |      case _ => 1
      |    .toString
      |    .match
      |      case _ => 1
      |
      |  foo0.toString
      |    .match
      |      case _ => 1
      |    .toString
      |    .match
      |      case _ => 1
      |
      |  foo0
      |    .match
      |      case _ => 1
      |    .toString
      |    .match
      |      case _ => 1
      |    .toString
      |
      |  foo0
      |    .match
      |      case _ => 1
      |    .match
      |      case _ => 1
      |    .toString
      |""".stripMargin
  )

  //SCL-22130
  def testMatchWithDotsWithoutBraces_WithInfixNotation(): Unit = doTextTest(
    """val value =
      |  1.match
      |    case 1 => 1
      |    case 2 => 2
      |  + 2
      |""".stripMargin
  )

  //SCL-22130
  def testMatchWithDotsWithoutBraces_WithInfixNotation_1(): Unit = doTextTest(
    """val value1 =
      |  1 + 1.match
      |    case 1 => 1
      |    case 2 => 2
      |  + 2
      |""".stripMargin
  )

  //SCL-22130
  def testMatchWithDotsWithoutBraces_WithInfixNotation_2(): Unit = {
    //TODO: the test fails, depends on SCL-22133
    return

    doTextTest(
      """val value2 =
        |  1
        |    + 1.match
        |      case 1 => 1
        |      case 2 => 2
        |    + 2
        |""".stripMargin
    )
  }

  //SCL-22130
  def testMatchWithDotsWithoutBraces_WithInfixNotation_3(): Unit = {
    //TODO: the test fails, depends on SCL-22133
    return

    doTextTest(
      """val value3 =
        |  1
        |    + 1.match
        |      case 1 => 1
        |      case 2 => 2
        |    + 2.match
        |      case 1 => 1
        |      case 2 => 2
        |""".stripMargin
    )
  }

  //SCL-22130
  def testMatchWithDotsWithoutBraces_WithInfixNotation_4(): Unit = doTextTest(
    """val value4 =
      |  1.match
      |    case 1 => 1
      |    case 2 => 2
      |  .match
      |    case 1 => 1
      |    case 2 => 2
      |  + 2
      |""".stripMargin
  )
}
