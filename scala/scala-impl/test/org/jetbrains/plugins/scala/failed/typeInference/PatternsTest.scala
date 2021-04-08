package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * Created by mucianm on 22.03.16.
  */
class PatternsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9137(): Unit = doTest()

  def testSCL9888():Unit = doTest()

  def testSCL8171(): Unit = {
    val text =
      s"""import scala.collection.immutable.NumericRange
          |
          |val seq = Seq("")
          |val x = seq match {
          |  case nr: NumericRange[_] => ${START}nr$END
          |  case _ => null
          |}
          |//NumericRange[_]""".stripMargin
    doTest(text)
  }

  // something seriously wrong, y isn't even a valid psi
  def testSCL4989(): Unit = {
    doTest(
      s"""
        |val x: Product2[Int, Int] = (10, 11)
        |val (y, _) = x
        |${START}y$END
        |//Int
      """.stripMargin)
  }

  def testSCL4487(): Unit = {
    doTest(
      s"""
         |def x(a: Int): String => Int = _ match {
         |  case value if value == "0" => ${START}a$END
         |}
         |//(String) => Int
      """.stripMargin)
  }

  def testSCL9241(): Unit = {
    doTest(
      s"""
         |trait Inv[A] { def head: A }
         |trait Cov[+A] { def head: A }
         |
         |def inv(i: Inv[Inv[String]]) = i match {
         |    case l: Inv[a] =>
         |      val x: a = ${START}l.head$END
         |  }
         |//a
      """.stripMargin)
  }

  def testSCL3170(): Unit = {
    doTest(
      s"""
         |trait M[A]
         |
         |  object N extends M[Unit]
         |
         |  def foo[A](ma: M[A]): A = ma match {
         |    case N => ${START}()$END
         |  }
         |//A
      """.stripMargin)
  }

  def testSCL6383(): Unit = {
    doTest(
      s"""
         |object Test {
         |  class R[T]
         |  case object MyR extends R[Int]
         |  def buggy[T] : PartialFunction[R[T], T] = { case MyR => ${START}3$END }
         |}
         |//T
      """.stripMargin)
  }

  def testSCL7418(): Unit = {
    doTest(
      s"""
         |trait Foo[A]
         |case class Bar(i: Int) extends Foo[Int]
         |
         |object Test {
         |  def test[A](foo: Foo[A]): A => String =
         |    a =>
         |      foo match {
         |        case Bar(i) => (a + ${START}1$END).toString
         |      }
         |}
         |//String
      """.stripMargin)
  }

  def testSCL5448(): Unit = {
    doTest(
      s"""
         |  case class Value[T](actual: T, numeric: Numeric[T])
         |
         |  def matcher(a: Any) = a match {
         |    case value: Value[_] => value.numeric.toDouble(${START}value.actual$END)
         |    case _ =>
         |  }
         |//_$$1
      """.stripMargin)
  }

  def testSCL8323(): Unit = {
    doTest(
      s"""
         |import scala.collection.Searching
         |import scala.collection.Searching.{Found, InsertionPoint}
         |
         |object CaseInsensitiveOrdering extends scala.math.Ordering[String] {
         |  def compare(a:String, b:String) = a.compareToIgnoreCase(b)
         |  def findClosest(s: String, availableNames: List[String]): String = {
         |    val sorted: List[String] = availableNames.sorted(CaseInsensitiveOrdering)
         |    Searching.search(sorted).search(s)(${START}CaseInsensitiveOrdering$END) match {
         |      case Found(_) => s
         |      case InsertionPoint(index) => sorted(index min sorted.size - 1)
         |    }
         |  }
         |}
         |//Ordering[Any]
      """.stripMargin)
  }

  def testSCL9094(): Unit = doTest()

  def testSCL10635(): Unit = {
    doTest(
      s"""
         |  sealed trait IO[A] {
         |    def flatMap[B](f: A => IO[B]): IO[B] =
         |      FlatMap(this, f)
         |  }
         |
         |  case class Return[A](a: A) extends IO[A]
         |
         |  case class FlatMap[A, B](sub: IO[A], k: A => IO[B]) extends IO[B]
         |
         |  def run[A](io: IO[A]): A = io match {
         |    case FlatMap(sub, f) => sub match {
         |      case Return(aSub) => run(f(${START}aSub$END))
         |    }
         |  }
         |//Nothing
      """.stripMargin)
  }

  def testSCL12174(): Unit = {
    doTest(
      s"""
         |def foo = (_:String).split(":") match {
         |    case x => ${START}x$END
         |}
         |//(String) => Array[String]
      """.stripMargin)
  }
}
