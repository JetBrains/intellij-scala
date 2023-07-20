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
}
