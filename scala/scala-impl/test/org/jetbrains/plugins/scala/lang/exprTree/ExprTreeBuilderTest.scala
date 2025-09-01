package org.jetbrains.plugins.scala.lang.exprTree

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

class ExprTreeBuilderTest extends SimpleScalaParserTestBase {
  def checkExprTree(code: String, expectedTreeText: String): Unit = {
    val psiTreeFile = parseScalaFile(code, ScalaVersion.Latest.Scala_2_13)
    val exprs = psiTreeFile
      .depthFirst(!_.is[ScExpression])
      .collect { case expr: ScExpression => expr }
      .toList
    assert(exprs.nonEmpty, "No expressions found")
    exprs.foreach { expr =>
      val exprTree = ExprTreeBuilder.build(expr)
      val treeText = ExprTreePrinter.print(exprTree)
      treeText.trim shouldBe expectedTreeText.trim
    }
  }

  def testLiteralInFunc(): Unit = checkExprTree(
    """
      |() => 1
      |""".stripMargin,
    """
      |fun()
      |  •body: lit:1
      |""".stripMargin
  )

  def testSimpleUnderscore(): Unit = checkExprTree(
    """
      |_
      |""".stripMargin,
    """
      |fun($_0)
      |  •body: $_0
      |""".stripMargin
  )

  def testRef(): Unit = checkExprTree(
    """
      |a.b
      |a b
      |""".stripMargin,
    """
      |ref:b
      |  •qual: ref:a
      |""".stripMargin
  )

  def testPrefix(): Unit = checkExprTree(
    """
      |a.unary_!
      |!a
      |""".stripMargin,
    """
      |ref:unary_!
      |  •qual: ref:a
      |""".stripMargin
  )

  def testUnderscoreInRef(): Unit = checkExprTree(
    """
      |_.a.b
      |(_.a).b
      |""".stripMargin,
    """
      |fun($_0)
      |  •body: ref:b
      |    •qual: ref:a
      |      •qual: $_0
      |""".stripMargin
  )

  def testEmptyArgList(): Unit = checkExprTree(
    """
      |fun()
      |fun.apply()
      |(fun)()
      |(fun.apply)()
      |(fun apply)()
      |""".stripMargin,
    """
      |call
      |  •target: ref:fun
      |  •args:
      |    •valueArgs: (empty)
      |""".stripMargin
  )

  def testOneArg(): Unit = checkExprTree(
    """
      |fun(1)
      |(fun)(1)
      |fun.apply(1)
      |fun apply 1
      |fun apply (1)
      |(fun.apply)(1)
      |(fun) apply 1
      |""".stripMargin,
    """
      |call
      |  •target: ref:fun
      |  •args:
      |    •valueArgs:
      |      •0: lit:1
      |""".stripMargin
  )

  def testMultipleArgs(): Unit = checkExprTree(
    """
      |fun(1, 2)(a, b = b)
      |(fun(1, 2))(a, b = b)
      |(fun apply (1, 2)) apply (a, b = b)
      |fun apply (1, 2) apply (a, b = b)
      |(fun apply) (1, 2) apply (a, b = b)
      |""".stripMargin,
    """
      |call
      |  •target: ref:fun
      |  •args:
      |    •valueArgs:
      |      •0: lit:1
      |      •1: lit:2
      |    •valueArgs:
      |      •0: ref:a
      |      •b: ref:b
      |""".stripMargin
  )

  def testGeneric(): Unit = checkExprTree(
    """
      |a.fun[Int](1)
      |a fun[Int] 1
      |a.fun.apply[Int](1)
      |a.fun apply[Int] 1
      |(a.fun) apply[Int] 1
      |(a.fun[Int])(1)
      |(a.fun.apply[Int])(1)
      |""".stripMargin,
    """
      |call
      |  •target: ref:fun
      |    •qual: ref:a
      |  •args:
      |    •typeArgs:
      |      •type:Int
      |    •valueArgs:
      |      •0: lit:1
      |""".stripMargin
  )

  def testUnderscoreInCall(): Unit = checkExprTree(
    """
      |fun(_.unary_!)
      |fun(!_)
      |fun(_ unary_!)
      |fun((_.unary_!))
      |fun((!_))
      |fun((_ unary_!))
      |fun((_).unary_!)
      |fun(!(_))
      |fun((_) unary_!)
      |""".stripMargin,
    """
      |call
      |  •target: ref:fun
      |  •args:
      |    •valueArgs:
      |      •0: fun($_0)
      |        •body: ref:unary_!
      |          •qual: $_0
      |""".stripMargin
  )

  def testUnderscoreInArgInner(): Unit = checkExprTree(
    """
      |call(fun(_))
      |call(fun apply _)
      |call(fun.apply(_))
      |
      |call apply (fun(_))
      |call apply (fun apply _)
      |call apply (fun apply (_))
      |call apply (fun.apply(_))
      |""".stripMargin,
    """
      |call
      |  •target: ref:call
      |  •args:
      |    •valueArgs:
      |      •0: fun($_0)
      |        •body: call
      |          •target: ref:fun
      |          •args:
      |            •valueArgs:
      |              •0: $_0
      |""".stripMargin
  )

  def testUnderscoreArgOuter(): Unit = checkExprTree(
    """
      |call apply fun(_)
      |call apply fun((_))
      |call apply fun(((_)))
      |""".stripMargin,
    """
      |fun($_0)
      |  •body: call
      |    •target: ref:call
      |    •args:
      |      •valueArgs:
      |        •0: call
      |          •target: ref:fun
      |          •args:
      |            •valueArgs:
      |              •0: $_0
      |""".stripMargin
  )
}
