package org.jetbrains.plugins.scala.codeInspection.unused.negative

import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedDeclarationInspectionTestBase

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
        |class Bar
        |class Baz
        |class Moo {
        |  def foo(_: Bar => Baz) = ???
        |  foo { implicit bar => new Baz  }
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

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
}
