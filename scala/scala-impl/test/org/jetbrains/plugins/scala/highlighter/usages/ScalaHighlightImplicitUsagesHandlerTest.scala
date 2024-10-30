package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.psi.PsiElement

class ScalaHighlightImplicitUsagesHandlerTest extends ScalaHighlightUsagesHandlerTestBase {

  override def createHandler: HighlightUsagesHandlerBase[PsiElement] =
    HighlightUsagesHandler.createCustomHandler(getEditor, getFile).asInstanceOf[HighlightUsagesHandlerBase[PsiElement]]

  def testNoUsages(): Unit = {
    val code =
      s"""
         |object AAA {
         |  def foo(i: Int): Int = i
         |  implicit val implicit${CARET}Int = 0
         |}
      """.stripMargin
    doTest(code, Seq("implicitInt"))
  }

  def testImplicitParameter(): Unit = {
    val code =
      s"""
         |object AAA {
         |  def foo()(implicit i: Int): Int = i
         |  implicit val impl${CARET}icitInt = 0
         |  foo()
         |}
      """.stripMargin
    doTest(code, Seq("implicitInt", "foo()"))
  }

  def testImplicitConversion(): Unit = {
    val code =
      s"""
         |object AAA {
         |  implicit def stri${CARET}ngToInt(s: String): Int = Integer.parseInt(s)
         |  def inc(i: Int): Int = i + 1
         |  inc("123")
         |}
      """.stripMargin
    doTest(code, Seq("stringToInt", "\"123\""))
  }

  def testBothParameterAndConversion(): Unit = {
    val code =
      s"""
         |object AAA {
         |  implicit def stri${CARET}ngToInt(s: String): Int = Integer.parseInt(s)
         |  def inc(i: Int): Int = i + 1
         |  def foo(s: String)(implicit converter: String => Int): Int = converter(s)
         |  inc("123")
         |  foo("42")
         |}
      """.stripMargin
    doTest(code, Seq("stringToInt", "\"123\"", "foo(\"42\")"))
  }

  def testHighlightedRangesAreCorrect(): Unit = {
    val code =
      s"""
         |object AAA {
         |  implicit val theAn${CARET}swer: Int = 42
         |  def increase(i: Int)(implicit summand: Int): Int = i + summand
         |  increase(0)
         |  this.increase(0)
         |  (this.increase)(0)
         |  increase       (0)
         |}
      """.stripMargin
    doTest(
      code,
      Seq(
        "theAnswer",
        "increase(0)",
        "increase(0)",
        "(this.increase)(0)",
        "increase       (0)"
      )
    )
  }

  def testApply(): Unit = {
    val code =
      s"""
         |object AAA {
         |  implicit val theAn${CARET}swer: Int = 42
         |  def apply(i: Int)(implicit suffix: Int): Int = i + suffix
         |  this(0)
         |}
      """.stripMargin
    doTest(code, Seq("theAnswer", "this(0)"))
  }

  def testContextBounds(): Unit = {
    val code =
      s"""
         |trait Semigroup[T] {
         |  def op(a: T, b: T): T
         |}
         |
         |implicit val intSem${CARET}igroup: Semigroup[Int] = (a: Int, b: Int) => a + b
         |
         |def double[T : Semigroup](t: T) = implicitly[Semigroup[T]].op(t, t)
         |
         |double(1)
      """.stripMargin
    doTest(code, Seq("intSemigroup", "double(1)"))
  }

  def testContextBoundsColon(): Unit = {
    val code =
      s"""
         |trait Semigroup[T] {
         |  def op(a: T, b: T): T
         |}
         |
         |implicit val intSemigroup: Semigroup[Int] = (a: Int, b: Int) => a + b
         |
         |def double[T $CARET: Semigroup](t: T) = implicitly[Semigroup[T]].op(t, t)
      """.stripMargin
    doTest(code, Seq("implicitly[Semigroup[T]]"))
  }

  def testContextBoundsColon2(): Unit = {
    val code =
      s"""
         |trait Semigroup[T] {
         |  def op(a: T, b: T): T
         |}
         |
         |implicit val intSemigroup: Semigroup[Int] = (a: Int, b: Int) => a + b
         |
         |def double[T:$CARET Semigroup](t: T) = implicitly[Semigroup[T]].op(t, t)
      """.stripMargin
    doTest(code, Seq("implicitly[Semigroup[T]]"))
  }

  def testImplicitClass(): Unit = {
    val code =
      s"""
         |object Test {
         |  implicit class ${CARET}StringExt(val s: String) {
         |    def twice: String = s + s
         |  }
         |  val string = "a"
         |  string.twice
         |}
       """.stripMargin
    doTest(code, Seq("string"))
  }

  def testConstructorParameter(): Unit = {
    val code =
      s"""
         |object Test {
         |  implicit val ${CARET}theAnswer: Int = 42
         |
         |  class AB(i: Int)(implicit j: Int) {
         |    def this(ints: Array[Int])(implicit j: Int) = this(ints(0))
         |  }
         |
         |  new AB(1)
         |  new AB(Array(1))
         |  new AB(2) with Serializable
         |
         |  class AC extends AB(3) with Serializable
         |}
       """.stripMargin
    doTest(code, Seq("theAnswer", "AB(1)", "AB(Array(1))", "AB(2)", "AB(3)"))
  }

  def testTypeClasses1(): Unit = {
    val code =
      s"""
         |object Test {
         |  trait Ordering[T]
         |
         |  implicit def seqDerivedOrdering[CC[X] <: scala.collection.Seq[X], T](implicit ord: Ordering[T]): Ordering[CC[T]] = ???
         |  implicit def tuple2Ordering[T1, T2](implicit ord1: Ordering[T1], ord2: Ordering[T2]): Ordering[(T1, T2)] = ???
         |
         |  implicit object ${CARET}BooleanOrdering extends Ordering[Boolean]
         |  implicit object IntOrdering extends Ordering[Int]
         |
         |  def sort[T](t: Seq[T])(implicit ordering: Ordering[T]) = ???
         |
         |  sort(Seq((Seq(12), (true, Seq(false)))))
         |  sort(Seq((Seq(12), false)))
         |  sort(Seq(false))
         |  sort(Seq((Seq(12), (1, Seq(2)))))
         |}
        """.stripMargin

    doTest(code, Seq(
      "BooleanOrdering",
      "sort(Seq((Seq(12), (true, Seq(false)))))",
      "sort(Seq((Seq(12), false)))",
      "sort(Seq(false))"))
  }

  def testTypeClasses2(): Unit = {
    val code =
      s"""
         |object Test {
         |  trait Ordering[T]
         |
         |  implicit def seqDerivedOrdering[CC[X] <: scala.collection.Seq[X], T](implicit ord: Ordering[T]): Ordering[CC[T]] = ???
         |  implicit def ${CARET}tuple2Ordering[T1, T2](implicit ord1: Ordering[T1], ord2: Ordering[T2]): Ordering[(T1, T2)] = ???
         |
         |  implicit object BooleanOrdering extends Ordering[Boolean]
         |  implicit object IntOrdering extends Ordering[Int]
         |
         |  def sort[T](t: Seq[T])(implicit ordering: Ordering[T]) = ???
         |
         |  sort(Seq((Seq(12), (true, Seq(false)))))
         |  sort(Seq((Seq(12), false)))
         |  sort(Seq(false))
         |  sort(Seq((Seq(12), (1, Seq(2)))))
         |}
        """.stripMargin

    doTest(code, Seq(
      "tuple2Ordering",
      "sort(Seq((Seq(12), (true, Seq(false)))))",
      "sort(Seq((Seq(12), false)))",
      "sort(Seq((Seq(12), (1, Seq(2)))))"))
  }

  def testCaretOnReference(): Unit = {
    val code =
      s"""
         |object AAA {
         |    def foo(i: Int)(implicit converter: Int => String): String = {
         |      111.toUpperCase()
         |      ${CARET}converter(i)
         |    }
         |  }
      """.stripMargin

    doTest(code, Seq("converter", "111", "converter"))
  }

  def testApplyFromConversion(): Unit = {
    val code =
      s"""
         |import java.util
         |import scala.collection.JavaConversions.${CARET}mapAsScalaMap
         |
         |object Test {
         |  def env: util.Map[String, String] = System.getenv() // Java map
         |  val term: String = env("TERM")
         |  val term2: String = env.apply("TERM")
         |}
        """.stripMargin

    doTest(code, Seq("mapAsScalaMap", "env", "env"))
  }

  def testChainedImplicitParameters1(): Unit = {
    val code =
      s"""
        |class A(val n: Int)
        |
        |class B(val m: Int, val n: Int)
        |
        |class C(val m: Int, val n: Int, val o: Int) {
        |  def total = m + n + o
        |}
        |
        |object O {
        |  implicit def ${CARET}toA(n: Int): A = new A(n)
        |
        |  implicit def aToB[A1](a: A1)(implicit f: A1 => A): B =
        |    new B(a.n, a.n)
        |
        |  implicit def bToC[B1](b: B1)(implicit f: B1 => B): C =
        |    new C(b.m, b.n, b.m + b.n)
        |
        |  5.total
        |  new A(5).total
        |  new B(5, 5).total
        |  new C(5, 5, 10).total
        |}
      """.stripMargin

    doTest(code, Seq("toA", "5"))
  }

  def testChainedImplicitParameters2(): Unit = {
    val code =
      s"""
         |class A(val n: Int)
         |
         |class B(val m: Int, val n: Int)
         |
         |class C(val m: Int, val n: Int, val o: Int) {
         |  def total = m + n + o
         |}
         |
         |object O {
         |  implicit def toA(n: Int): A = new A(n)
         |
         |  implicit def ${CARET}aToB[A1](a: A1)(implicit f: A1 => A): B =
         |    new B(a.n, a.n)
         |
         |  implicit def bToC[B1](b: B1)(implicit f: B1 => B): C =
         |    new C(b.m, b.n, b.m + b.n)
         |
         |  5.total
         |  new A(5).total
         |  new B(5, 5).total
         |  new C(5, 5, 10).total
         |}
        """.stripMargin

    doTest(code, Seq("aToB", "5", "new A(5)"))
  }

  def testChainedImplicitParameters3(): Unit = {
    val code =
      s"""
         |class A(val n: Int)
         |
         |class B(val m: Int, val n: Int)
         |
         |class C(val m: Int, val n: Int, val o: Int) {
         |  def total = m + n + o
         |}
         |
         |object O {
         |  implicit def toA(n: Int): A = new A(n)
         |
         |  implicit def aToB[A1](a: A1)(implicit f: A1 => A): B =
         |    new B(a.n, a.n)
         |
         |  implicit def ${CARET}bToC[B1](b: B1)(implicit f: B1 => B): C =
         |    new C(b.m, b.n, b.m + b.n)
         |
         |  5.total
         |  new A(5).total
         |  new B(5, 5).total
         |  new C(5, 5, 10).total
         |}
        """.stripMargin

    doTest(code, Seq("bToC", "5", "new A(5)", "new B(5, 5)"))
  }
}
