package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert

class ContextFunctionTypeInferenceTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private def testImplicits(fileText: String, problemsExpected: Boolean = false): Unit = {
    val file = configureFromFileText(fileText)

    val problems = file.depthFirst().filterByType[ScExpression].flatMap { e =>
      e.findImplicitArguments.toSeq.flatMap { seq =>
        seq.filter(_.isImplicitParameterProblem)
      }
    }.toSeq

    Assert.assertTrue(
      "Problems in implicit parameters search: " + problems.mkString("\n"),
      problemsExpected == problems.nonEmpty
    )
  }

  def testSimpleTypeInference(): Unit = doTest(
    s"""
       |object A {
       |  def foo[A](fn: A ?=> Int): A = ???
       |  ${START}foo(implicitly[Int])$END
       |}
       |//Int
       |""".stripMargin
  )

  def testResolvesImplicitInsideBody(): Unit = testImplicits(
    """
      |object A {
      |  val x: String ?=> String = implicitly[String]
      |  val y: String ?=> String = (s: String) ?=> implicitly[String]
      |}
      |""".stripMargin
  )

  def testTypeConstructorInference(): Unit = doTest(
    s"""
       |object A {
       |  trait Foo[A]
       |  def foo[A](fn: Foo[A] ?=> Int): A = ???
       |  ${START}foo {
       |    val x = implicitly[Foo[String]]
       |    123
       |  }$END
       |}
       |//String
       |""".stripMargin
  )

  def testMultipleUsages(): Unit = doTest(
    s"""
       |object A {
       |  def foo[A](fn: A ?=> Any): A = ???
       |  ${START}foo(implicitly[String] + implicitly[Int])$END
       |}
       |//String with Int
       |""".stripMargin
  )

  def testTypeConstructorCovariant(): Unit = doTest(
    s"""
       |object A {
       |  trait Foo[+A]
       |  def foo[A](fn: Foo[A] ?=> Int): A = ???
       |  ${START}foo {
       |    implicitly[Foo[String]]
       |    implicitly[Foo[Int]]
       |    123
       |  }$END
       |}
       |//Int with String
       |""".stripMargin
  )

  def testTypeConstructorContravariant(): Unit = doTest(
    s"""
       |object A {
       |  trait Foo[-A]
       |  def foo[A](fn: Foo[A] ?=> Int): A = ???
       |  ${START}foo {
       |    implicitly[Foo[String]]
       |    implicitly[Foo[Int]]
       |    123
       |  }$END
       |}
       |//Any
       |""".stripMargin
  )

  def testTypeConstructorInvariant(): Unit = testImplicits(
    s"""
       |object A {
       |  trait Foo[A]
       |  def foo[A](fn: Foo[A] ?=> Int): A = ???
       |  foo {
       |    implicitly[Foo[String]]
       |    implicitly[Foo[Int]]
       |    123
       |  }
       |}
       |""".stripMargin,
      problemsExpected = true
  )

  def testMultipleTypeVaribles(): Unit = doTest(
    s"""
       |object A {
       |  trait Foo[A, +B]
       |  def foo[A, B](fn: Foo[A, B] ?=> Int): Foo[A, B] = ???
       |  ${START}foo {
       |    implicitly[Foo[Int, String]]
       |    implicitly[Foo[Int, Int]]
       |    123
       |  }$END
       |}
       |//A.Foo[Int, Int with String]
       |""".stripMargin
  )

  def testSCL21687(): Unit = doTest(
    s"""
       |object A {
       |  def contextual[A](f: String ?=> A): A = ???
       |  def test() = contextual(${START}Option("").map(_ => 1).getOrElse(2)$END)
       |}
       |//String ?=> Int
       |""".stripMargin
  )
}
