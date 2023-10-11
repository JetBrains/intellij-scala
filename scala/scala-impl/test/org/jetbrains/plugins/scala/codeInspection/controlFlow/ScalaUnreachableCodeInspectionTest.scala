package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class ScalaUnreachableCodeInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ScalaUnreachableCodeInspection]

  override protected def description: String = ScalaInspectionBundle.message("unreachable.code.name")

  def test_throw(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  throw new Exception()
       |  ${START}println("test")
       |  println("test 2")$END
       |}
       |""".stripMargin
  )

  def test_try_catch(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  try {
       |    throw new Exception()
       |    ${START}println("test")
       |    println("test 2")$END
       |  } catch {
       |   case e =>
       |  }
       |
       |  println("blub")
       |}
       |""".stripMargin
  )

  def test_try_finally(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  try {
       |    throw new Exception()
       |    ${START}println("test")
       |    println("test 2")$END
       |  } finally {
       |    println("blub")
       |  }
       |
       |  ${START}println("blub")$END
       |}
       |""".stripMargin
  )

  /*def test_while(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  while(true) {
       |  }
       |
       |  ${START}println("blub")$END
       |}
       |""".stripMargin
  )*/

  def test_return_in_inner_func(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  some_method {
       |    return
       |    ${START}println("haha")$END
       |  }
       |
       |  println("blub")
       |}
       |""".stripMargin
  )

  def test_throw_in_by_name_arg(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  def take_by_name(x: => Any) = ()
       |
       |  take_by_name {
       |    throw new Exception()
       |    ${START}val x = 0$END
       |  }
       |
       |  val y = 0
       |}
       |""".stripMargin
  )

  def test_throw_in_by_value_arg(): Unit = checkTextHasError(
    s"""
       |def take_by_value(x: Any) = ()
       |
       |def test(): Unit = {
       |  take_by_value {
       |    throw new Exception()
       |    ${START}println("haha")$END
       |  }
       |
       |  ${START}println("blub")$END
       |}
       |""".stripMargin
  )

  def test_throw_in_unresolved_arg(): Unit = checkTextHasError(
    s"""
       |def test(): Unit = {
       |  unknown_method {
       |    throw new Exception()
       |    ${START}println("haha")$END
       |  }
       |
       |  println("blub")
       |}
       |""".stripMargin
  )

  // SCL-9005
  def test_try_in_finally(): Unit = checkTextHasNoErrors(
    s"""
       |object Test {
       |  def test: Boolean = {
       |    val returnValue = true
       |
       |    try {
       |    } catch {
       |      case e: Exception =>
       |    } finally {
       |      try {
       |      } catch {
       |        case e: Exception =>
       |      }
       |    }
       |
       |    true // Reachable
       |    returnValue // Marked as "unreachable"
       |    true        // Reachable
       |  }
       |}
       |""".stripMargin
  )

  // SCL-13557
  def test_try_in_finally2(): Unit = checkTextHasNoErrors(
    """
      |def test = {
      |  try {
      |  } finally {
      |    try {
      |    } catch {
      |     case _: Throwable =>
      |    }
      |  }
      |  List("test")
      |}
      |""".stripMargin
  )

  // SCL-14070
  def test_14070(): Unit = checkTextHasError(
    s"""def stream: Stream[Int] = 5 #:: {
      |  throw new RuntimeException("unexpected")
      |} ${START}#:: Stream.empty[Int]$END
      |""".stripMargin
  )

  def test_SCL_17712_1(): Unit = checkTextHasError(
    s"""
       |def foo = {
       |  println(1)
       |  println(1)
       |
       |  try {
       |    println(1)
       |    throw new RuntimeException("Ahaha")
       |    ${START}println(1)$END
       |  } catch {
       |    case _: Throwable =>
       |  }
       |
       |  println(1)
       |
       |  Try {
       |    println(1)
       |    throw new RuntimeException("Ahaha")
       |    ${START}println(1)$END
       |  }
       |
       |  println(1)
       |  println(1)
       |}
       |""".stripMargin
  )

  def test_SCL_17712_2(): Unit = checkTextHasError(
    s"""
       |def bar = {
       |  println(1)
       |  println(1)
       |
       |  val tri = Try {
       |    println(1)
       |    println(1)
       |    throw new InvalidRepoException("Mortems crescere in tolosa!")
       |    ${START}println(1)
       |    println(1)$END
       |  }
       |  tri.toEither.left.map(Some(_)).map(_ => ())
       |
       |  println(1)
       |  println(1)
       |}
       |
       |""".stripMargin
  )

  def test_SCL_18930(): Unit = checkTextHasError(
    s"""
       |def fooOK1(): Unit = {
       |  println("outside try 1")
       |
       |  try {
       |    println("in try 1")
       |    throw new RuntimeException("test")
       |    ${START}println("in try 2")$END
       |  } catch {
       |    case ex: Throwable =>
       |      println("in catch")
       |  } finally {
       |    println("in finally")
       |  }
       |
       |  println("outside try 1")
       |}
       |fooOK1()
       |
       |
       |def fooOK2(): Unit = {
       |  println("outside try 1")
       |
       |  try {
       |    println("in try 1")
       |    throw new RuntimeException("test")
       |    ${START}println("in try 2")$END
       |  }
       |
       |  ${START}println("outside try 1")$END
       |}
       |fooOK2()
       |
       |
       |def fooBAD1(): Unit = {
       |  println("outside try 1")
       |
       |  try {
       |    println("in try 1")
       |    throw new RuntimeException("test")
       |    ${START}println("in try 2")$END
       |  } catch {
       |    case ex: Throwable =>
       |      println("in catch")
       |      throw ex
       |  } finally {
       |    println("in finally")
       |  }
       |
       |  ${START}println("outside try 1")$END
       |}
       |fooBAD1()
       |
       |
       |def fooBAD2(): Unit = {
       |  println("outside try 1")
       |
       |  try {
       |    println("in try 1")
       |    throw new RuntimeException("test")
       |    ${START}println("in try 2")$END
       |  } finally {
       |    println("in finally")
       |  }
       |
       |  ${START}println("outside try 1")$END
       |}
       |fooBAD2()
       |""".stripMargin
  )
}