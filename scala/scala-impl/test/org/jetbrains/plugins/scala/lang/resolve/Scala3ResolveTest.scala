package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3ResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testSummon(): Unit = {
    doResolveTest(
      s"""class A {
         |  def ${REFTGT}bar(): Unit = ()
         |}
         |given A = new A
         |
         |def foo(): Unit = {
         |  summon[A].${REFSRC}bar()
         |}
         |""".stripMargin)

    doResolveTest(
      s"def foo: String = Predef.${REFSRC}summon[String]")
  }

  def testStrictEquality(): Unit = doResolveTest(
    s"import scala.language.${REFSRC}strictEquality"
  )

  def testGivenAliasParam(): Unit = doResolveTest(
    s"given mySummon[A](using ${REFTGT}value: A): A = ${REFSRC}value"
  )

  def testTypeVariableMatch(): Unit = doResolveTest(
    s"??? match { case _: Seq[${REFTGT}x] => ??? : ${REFSRC}x }"
  )

  def testTypeVariableMatchNested(): Unit = doResolveTest(
    s"??? match { case _: Seq[Seq[${REFTGT}x]] => ??? : ${REFSRC}x }"
  )

  def testTypeVariableMatchParentheses(): Unit = doResolveTest(
    s"??? match { case _: (Seq[${REFTGT}x]) => ??? : ${REFSRC}x }"
  )

  def testTypeVariableMatchInfix(): Unit = doResolveTest(
    s"class &&[A, B]; ??? match { case _: (${REFTGT}x && y) => ??? : ${REFSRC}x }"
  )

  def testTypeVariableMatchType(): Unit = doResolveTest(
    s"type T = Seq[Int] match { case Seq[${REFTGT}x] => Option[${REFSRC}x] }"
  )

  def testTypeVariableMatchTypeNested(): Unit = doResolveTest(
    s"type T = Seq[Int] match { case Seq[Seq[${REFTGT}x]] => Option[${REFSRC}x] }"
  )

  def testTypeVariableMatchTypeParentheses(): Unit = doResolveTest(
    s"type T = Seq[Int] match { case (Seq[${REFTGT}x]) => Option[${REFSRC}x] }"
  )

  def testTypeVariableMatchTypeInfix(): Unit = doResolveTest(
    s"class &&[A, B]; type T = Int && Long match { case ${REFTGT}x && y => ${REFSRC}x }"
  )

  def testImplicitValByGivenWildcard(): Unit = doResolveTest(
    s"""
       |object Usages:
       |  import Declarations.given
       |  val s = st${REFSRC}r
       |
       |object Declarations:
       |  implicit val s${REFTGT}tr: String = "foo"
       |""".stripMargin
  )

  def testImplicitValByGivenSelector(): Unit = doResolveTest(
    s"""object Usages:
       |  import Declarations.given String
       |  val s = st${REFSRC}r
       |
       |object Declarations:
       |  implicit val (s${REFTGT}tr, i) = ("foo", 1)
       |""".stripMargin
  )

  def testImplicitDefByGivenWildcard(): Unit = doResolveTest(
    s"""import scala.language.implicitConversions
       |
       |object Usages:
       |  import Declarations.given
       |  val s: Long = lo${REFSRC}ng
       |
       |object Declarations:
       |  given Int = 1
       |  implicit def lo${REFTGT}ng(using i: Int): Long = i + 2L
       |""".stripMargin
  )

  def testImplicitClassByGivenWildcard(): Unit = doResolveTest(
    s"""object Usages:
       |  import Declarations.given
       |  val str: Long = "foobar".scre${REFSRC}am
       |
       |object Declarations:
       |  implicit class StringExt(private val s: String) extends AnyVal:
       |    def scream: String = s.toUpperCase
       |""".stripMargin
  )

  //SCL-21604
  def testNamelessUsingParameterType_ClashBetweenTypeAndObject_1(): Unit = doResolveTest(
    s"""type MyClass = Int
       |object MyClass:
       |  def ${REFTGT}test(): String = ""
       |
       |def foo(using MyClass): Unit =
       |  summon[MyClass]
       |  MyClass.${REFSRC}test()
       |""".stripMargin
  )

  //SCL-21604
  def testNamelessUsingParameterType_ClashBetweenTypeAndObject_2(): Unit = doResolveTest(
    s"""type ${REFTGT}MyClass = Int
       |object MyClass:
       |  def test(): String = ""
       |
       |def foo(using MyClass): Unit =
       |  summon[${REFSRC}MyClass]
       |  MyClass.test()
       |""".stripMargin
  )

  //SCL-21835
  def testNamelessUsingParameterSyntheticName_1(): Unit = doResolveTest(
    s"""def foo(x: Int)
       |       (using ${REFTGT}Int, String)
       |       (y: Int)
       |       (using Short) =
       |  ${REFSRC}x$$2
       |  x$$3
       |  x$$5
       |""".stripMargin
  )

  //SCL-21835
  def testNamelessUsingParameterSyntheticName_2(): Unit = doResolveTest(
    s"""def foo(x: Int)
       |       (using Int, ${REFTGT}String)
       |       (y: Int)
       |       (using Short) =
       |  x$$2
       |  ${REFSRC}x$$3
       |  x$$5
       |""".stripMargin
  )

  //SCL-21835
  def testNamelessUsingParameterSyntheticName_3(): Unit = doResolveTest(
    s"""def foo(x: Int)
       |       (using Int, String)
       |       (y: Int)
       |       (using ${REFTGT}Short) =
       |  x$$2
       |  x$$3
       |  ${REFSRC}x$$5
       |""".stripMargin
  )
}
