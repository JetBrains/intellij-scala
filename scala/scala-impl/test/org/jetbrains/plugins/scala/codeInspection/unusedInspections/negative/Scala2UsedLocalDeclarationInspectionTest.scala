package org.jetbrains.plugins.scala.codeInspection.unusedInspections.negative

import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedDeclarationInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedDeclarationInspection
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.junit.Assert.assertTrue

class Scala2UsedLocalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_sugar_op(): Unit = checkTextHasNoErrors(
    s"""object Ctx {
       |  private class Test {
       |    def +(_: Any): Test = this
       |  }
       |  private var test = new Test
       |  test += 3
       |}
       |Ctx
       |""".stripMargin
  )

  def test_property_assignment(): Unit = checkTextHasNoErrors(
    s"""object Test {
       |  private def data: Int = 0
       |  private def data_=(i: Int): Int = i
       |
       |  Test.data = 3
       |}
       |""".stripMargin
  )

  def test_app_entry_point(): Unit = checkTextHasNoErrors(
    "object Foo extends App"
  )

  def test_main_method_entry_point(): Unit = checkTextHasNoErrors(
    "object Foo { def main(thisNameCanBeAnything: Array[String]) = {} }"
  )

  def test_suppressed(): Unit = {
    val code =
      """
        |@scala.annotation.unused class Bar {
        |  //noinspection ScalaUnusedSymbol
        |  private val f = 2
        |
        |  @scala.annotation.unused def aa(): Unit = {
        |    //noinspection ScalaUnusedSymbol
        |    val d = 2
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testNoHighlightWildCards(): Unit = {
    val code =
      """
        |class Moo {
        |  def foo(i: Any): Unit = i match {
        |    case _: String => println()
        |    case b: Seq[String] => b.foreach(_ => println())
        |    case t: (String, Int) =>
        |      val (s, _) = t
        |      println(s)
        |    case _ =>
        |      for (_ <- 1 to 2) {
        |        println()
        |      }
        |  }
        |}
        |new Moo().foo("")
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testImplicitParameter(): Unit = {
    val code =
      """
        |import scala.annotation.unused
        |class Bar
        |@unused class Baz(@unused bar: Bar)
        |@unused class Moo {
        |  def foo(@unused bar: Bar => Baz) = {}
        |  foo { implicit bar => new Baz(bar) }
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def test_public_implicit_parameter(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass()
       |  (implicit val param: Boolean) {
       |}
       |""".stripMargin
  )

  def test_protected_implicit_parameter(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass()
       |  (implicit protected val param: Boolean) {
       |}
       |""".stripMargin
  )

  def test_public_implicit_val(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass() {
       |  implicit val field: Boolean = false
       |}
       |""".stripMargin
  )

  def test_protected_implicit_val(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass() {
       |  implicit protected val field: Boolean = false
       |}
       |""".stripMargin
  )

  def test_implicit_private_this_implicit_parameter(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass()
       |  (implicit param: Boolean) {
       |  MyClass.foo()
       |}
       |
       |object MyClass {
       |  def foo()(implicit p: Boolean): Boolean = p
       |}
       |""".stripMargin
  )

  def test_private_this_implicit_parameter(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass()
       |  (implicit private[this] val param: Boolean) {
       |  MyClass.foo()
       |}
       |
       |object MyClass {
       |  def foo()(implicit p: Boolean): Boolean = p
       |}
       |""".stripMargin
  )

  def test_private_implicit_parameter(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass()
       |  (implicit private val param: Boolean) {
       |  MyClass.foo()
       |}
       |
       |object MyClass {
       |  def foo()(implicit p: Boolean): Boolean = p
       |}
       |""".stripMargin
  )

  def test_private_this_implicit_val(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass() {
       |  implicit private[this] val param: Boolean = false
       |  MyClass.foo()
       |}
       |
       |object MyClass {
       |  def foo()(implicit p: Boolean): Boolean = p
       |}
       |""".stripMargin
  )

  def test_private_implicit_val(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused
       |class MyClass() {
       |  implicit private val param: Boolean = false
       |    MyClass.foo()
       |}
       |
       |object MyClass {
       |  def foo()(implicit p: Boolean): Boolean = p
       |}
       |""".stripMargin
  )

  // SCL-17181
  def test_overriding_declaration(): Unit = checkTextHasNoErrors(
    """
      |trait A {
      |  val foo: Int
      |}
      |new A {
      |  override val foo: Int = 1 // should not be marked as unused
      |}
      |""".stripMargin
  )

  // SCL-16919
  def test_overridden_declaration(): Unit = checkTextHasNoErrors(
    """
      |class A {
      |  def foo(str: String): Unit = ()
      |}
      |new A {
      |  override def foo(str: String): Unit = {
      |    println(str)
      |  }
      |}
      |""".stripMargin
  )

  def testOverriddenTypeMember(): Unit = checkTextHasNoErrors(
    """
      |import scala.annotation.unused
      |private trait Base {
      |  type ty
      |}
      |
      |@unused
      |private class Test extends Base {
      |  override type ty = Int
      |}
      |""".stripMargin
  )

  // SCL-17662
  def test_private_auxiliary_constructor_is_used_by_companion_object(): Unit = checkTextHasNoErrors(
    s"""
       |class Test(val s: Int) {
       |  private def this() = this(3)
       |}
       |object Test {
       |  def foo() = new Test()
       |}
       |Test.foo.s
       |""".stripMargin
  )

  // SCL-17662
  def test_private_auxiliary_constructor_is_used_by_other_constructor(): Unit = checkTextHasNoErrors(
    s"""
       |class Test(val s: String, val i: Int) {
       |  private def this(s: String) = this(s, 42)
       |  def this(i: Int) = this(i.toString)
       |  s + i
       |}
       |new Test(0)
       |""".stripMargin
  )

  // SCL-17662
  def test_private_auxiliary_constructor_is_used_within_same_class(): Unit = checkTextHasNoErrors(
    s"""
       |class Test(s: String) {
       |  private def this(i: Int) = this(i.toString)
       |  new Test(42).s
       |}
       |""".stripMargin
  )

  // SCL-18600
  def testUnusedAnnotation(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.annotation.unused
       |Test
       |object Test {
       |  @unused
       |  private def test(): Unit = ()
       |  @unused
       |  private case class Test1()
       |  @unused
       |  private class Test2
       |  @unused
       |  private object Test3
       |}
       |""".stripMargin
  )

  def testNowarnAnnotation(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.annotation.nowarn
       |
       |@nowarn("unused")
       |object Test {
       |  @nowarn("unused")
       |  private def test(): Unit = ()
       |  @nowarn("unused")
       |  private case class Test1()
       |  @nowarn("unused")
       |  private class Test2
       |  @nowarn("unused")
       |  private object Test3
       |}
       |""".stripMargin
  )

  def test_private_case_class(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused final class Foo {
       |  private case class Bar()
       |  Bar()
       |}
       |""".stripMargin)

  def test_class_type_parameter1(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test[A] { Seq.empty[A] }
       |""".stripMargin
  )

  def test_class_type_parameter2(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test[A, B] { Seq.empty[A]; Seq.empty[B] }
       |""".stripMargin
  )

  def test_class_type_parameter3(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test[A <: java.lang.Object] { Seq.empty[A] }
       |""".stripMargin
  )

  def test_function_type_parameter1(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test {
       |  @scala.annotation.unused def foo[A] = { Seq.empty[A] }
       |}
       |""".stripMargin
  )

  def test_function_type_parameter2(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test {
       |  @scala.annotation.unused def foo[A, B] = { Seq.empty[A]; Seq.empty[B] }
       |}
       |""".stripMargin
  )

  def test_function_type_parameter3(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test {
       |  @scala.annotation.unused def foo[A, B <: java.lang.Object] = { Seq.empty[A]; Seq.empty[B] }
       |}
       |""".stripMargin
  )

  def test_type_parameters_are_not_inspected_in_batch_mode(): Unit = {
    configureByText("class Test[A]")
    val unusedTypeParam = getFile.findElementAt(11).getContext
    assert(unusedTypeParam.isInstanceOf[ScTypeParam])
    val problems = (new ScalaUnusedDeclarationInspection).invoke(unusedTypeParam, isOnTheFly = false)
    assertTrue(s"Found ${problems.size} problem(s) while 0 were expected", problems.isEmpty)
  }

  def test_context_bounded_class_type_parameter1(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test[A <: java.lang.Object] {}
       |""".stripMargin
  )

  def test_context_bounded_class_type_parameter2(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test[A : Ordering] {}
       |""".stripMargin
  )

  def test_view_bounded_class_type_parameter(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test[A <% Ordering[A]] {}
       |""".stripMargin
  )

  def test_context_bounded_function_type_parameter1(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test {
       |  @scala.annotation.unused def foo[A <: java.lang.Object] = { }
       |}
       |""".stripMargin
  )

  def test_context_bounded_function_type_parameter2(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test {
       |  @scala.annotation.unused def foo[A : Ordering] = { }
       |}
       |""".stripMargin
  )

  def test_view_bounded_function_type_parameter(): Unit = checkTextHasNoErrors(
    s"""
       |@scala.annotation.unused class Test {
       |  @scala.annotation.unused def foo[A <% Ordering[A]] = { }
       |}
       |""".stripMargin
  )

  def test_single_abstract_method(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.annotation.unused
       |@unused object ctx {
       |  private abstract class SamContainer { def iAmSam(foobar: Int): Unit }
       |  @unused class SamConsumer { @unused val samContainer: SamContainer = (i: Int) => println(i) }
       |}
       |""".stripMargin
  )
  
  def test_parameter_of_abstract_method(): Unit = checkTextHasNoErrors(
    s"""import scala.annotation.unused
       |@unused trait Context {
       |  @unused def someAbstractMethod1(unusedParam: Int): Unit
       |  @unused def someAbstractMethod2(unusedParam: Int): Unit
       |}
       |""".stripMargin
  )
}
