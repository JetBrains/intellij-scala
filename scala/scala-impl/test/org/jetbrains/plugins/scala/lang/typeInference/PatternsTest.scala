package org.jetbrains.plugins.scala.lang.typeInference

class PatternsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9137(): Unit = doTest()

  def testSCL9888():Unit = doTest()

  //SCL-8171
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
    doTest(text, failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail = false)
  }

  def testSCL3170(): Unit = {
    doTest(
      s"""
         |trait M[A]
         |
         |  object N extends M[Unit]
         |
         |  def foo[A](ma: M[A]): A = ma match {
         |    case N => $START()$END
         |  }
         |//Unit
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
         |        case Bar(i) => (${START}a$END + 1).toString
         |      }
         |}
         |//Int
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
         |//_
      """.stripMargin)
  }

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
         |//Any
      """.stripMargin)
  }

  def testSCL12908(): Unit = {
    val text =
      """
        |def check[T](array: Array[T]): Unit = {
        |    array match {
        |      case bytes: Array[Byte] =>
        |        println("Got bytes!")
        |      case _ =>
        |        println("Got something else than bytes!")
        |    }
        |  }
        """.stripMargin
    checkTextHasNoErrors(text)
  }
}
