package org.jetbrains.plugins.scala
package codeInspection
package forwardReferenceInspection

import com.intellij.codeInspection.LocalInspectionTool

class ForwardReferenceInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ForwardReferenceInspection]

  override protected val description =
    ScalaBundle.message("suspicicious.forward.reference.template.body")


  def test_forward_ref(): Unit = checkTextHasError(
    s"""class Test {
       |  val test = ${START}ref$END
       |
       |  val ref = 4
       |}"""
  )

  def test_forward_ref_in_expr(): Unit = checkTextHasError(
    s"""class Test {
       |  val test = ${START}ref$END + 5
       |
       |  val ref = 4
       |}"""
  )

  def test_forward_ref_in_match(): Unit = checkTextHasError(
    s"""class Test {
       |  val test =
       |    5 match {
       |      case x => x == ${START}ref$END
       |    }
       |
       |  val ref = 4
       |}"""
  )

  def test_SCL15872(): Unit = checkTextHasError(
    s"""class Foo {
       |  println(${START}a$END)
       |  val a = 4
       |}"""
  )

  def test_lazy_val(): Unit = checkTextHasNoErrors(
    s"""class Test {
       |  lazy val test = ref
       |
       |  val ref = 4
       |}"""
  )

  def test_def(): Unit = checkTextHasNoErrors(
    s"""class Test {
       |  def test = ref
       |
       |  val ref = 4
       |}"""
  )

  def test_backward_ref(): Unit = checkTextHasNoErrors(
    s"""class Test {
       |  val ref = 4
       |
       |  val test = ref
       |}"""
  )

  def test_referencing_lazy_val(): Unit = checkTextHasNoErrors(
    s"""class Test {
       |  val test = ref
       |
       |  lazy val ref = 4
       |}"""
  )

  def test_in_class(): Unit = checkTextHasError(
    s"""
       |trait Inner {
       |  def inner: Int
       |}
       |
       |class Test {
       |  val test = new Inner {
       |    val inner = ${START}ref$END
       |  };
       |
       |  val ref = 4
       |}"""
  )

  def test_different_instance(): Unit = checkTextHasNoErrors(
    s"""class Test(outer: Test) {
       |  val test = outer.ref
       |
       |  val ref = 5
       |}"""
  )

  def test_SCL15827_1(): Unit = checkTextHasNoErrors(
    """
      |class Suspicious {
      |  val foo: Function1[Int, Unit] = new Function1[Int, Unit] {
      |    override def apply(x: Int): Unit = {
      |      x match {
      |        case `value` =>
      |        case _ =>
      |      }
      |    }
      |  }
      |
      |  private val value = 42
      |}
      |""".stripMargin
  )


  def test_SCL15827_2(): Unit = checkTextHasNoErrors(
    """
      |class Suspicious {
      |  val bar: Function1[Int, Unit] = {
      |    case `value` =>
      |    case _ =>
      |  }
      |
      |  private val value = 42
      |}
      |""".stripMargin
  )

  def test_object(): Unit = checkTextHasNoErrors(
    """
      |class Suspicious {
      |  object inner {
      |    val test = ref
      |  }
      |
      |  private val ref = 42
      |}
      |""".stripMargin
  )

  def test_SCL15827_3(): Unit = checkTextHasNoErrors(
    """
      |class Suspicious {
      |  val baz: Function1[Int, Unit] = x => x match {
      |    case `value` =>
      |    case _ =>
      |  }
      |  private val value = 42
      |}
      |""".stripMargin
  )

  def test_NoAstLoadingOtherFile(): Unit = checkTextHasNoErrors(
    """
      |class Test {
      |  val right: Either[String, Int] = Right(1)
      |}
      |""".stripMargin
  )

}