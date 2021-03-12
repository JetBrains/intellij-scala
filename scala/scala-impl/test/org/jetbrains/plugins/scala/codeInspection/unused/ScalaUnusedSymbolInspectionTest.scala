package org.jetbrains.plugins.scala.codeInspection.unused

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class ScalaUnusedSymbolInspectionTest extends ScalaUnusedSymbolInspectionTestBase {

  def testPrivateField(): Unit = {
    val code =
      s"""
         |class Foo {
         |  private val ${START}s$END = 0
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Foo {
        |  private val s = 0
        |}
      """.stripMargin
    val after =
      """
        |class Foo {
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testLocalUnusedSymbol(): Unit = {
    val code =
      s"""
         |object Foo {
         |  def foo(): Unit = {
         |    val ${START}s$END = 0
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |object Foo {
        |  def foo(): Unit = {
        |    val s = 0
        |  }
        |}
      """.stripMargin
    val after =
      """
        |object Foo {
        |  def foo(): Unit = {
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testNonPrivateField(): Unit = {
    val code =
      """
        |class Foo {
        |  val s: String = ""
        |  protected val z: Int = 2
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testRemoveMultiDeclaration(): Unit = {
    val code =
      s"""
         |class Foo {
         |  private val (${START}a$END, b): String = ???
         |  println(b)
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Foo {
        |  private val (a, b): String = ???
        |  println(b)
        |}
      """.stripMargin
    val after =
      """
        |class Foo {
        |  private val (_, b): String = ???
        |  println(b)
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testSupressed(): Unit = {
    val code =
      """
        |class Bar {
        |  //noinspection ScalaUnusedSymbol
        |  private val f = 2
        |
        |  def aa(): Unit = {
        |    //noinspection ScalaUnusedSymbol
        |    val d = 2
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testLocalVar(): Unit = {
    val code =
      s"""
         |class Bar {
         |  def aa(): Unit = {
         |    var (${START}d$END, a) = 10
         |  }
         |}
      """.stripMargin
    checkTextHasError(code, allowAdditionalHighlights = true)
    val before =
      s"""
         |class Bar {
         |  def aa(): Unit = {
         |    var (d, a) = 10
         |    println(a)
         |  }
         |}
      """.stripMargin
    val after =
      s"""
         |class Bar {
         |  def aa(): Unit = {
         |    var (_, a) = 10
         |    println(a)
         |  }
         |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testMatchCaseWithType(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option(null) match {
         |    case Some(${START}s$END: String) =>
         |      println("AA")
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(s: String) =>
        |      println("AA")
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(_: String) =>
        |      println("AA")
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testMatchCaseNoType(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option(null) match {
         |    case Some(${START}s$END) =>
         |      println("AA")
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(s) =>
        |      println("AA")
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(_) =>
        |      println("AA")
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testAnonymousFunctionDestructor(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option("").map {
         |    case ${START}a$END: String =>
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  Option("").map {
        |    case a: String =>
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option("").map {
        |    case _: String =>
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testBindingPattern(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option(null) match {
         |    case ${START}s$END@Some(a) => println(a)
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(a) => println(a)
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(a) => println(a)
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testBindingPattern2(): Unit = {
    val code =
      s"""
         |class Moo {
         |  Option(null) match {
         |    case s@Some(${START}a$END) => println(s)
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(a) => println(s)
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(_) => println(s)
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testAnonymousFunctionWithCaseClause(): Unit = {
    val code =
      s"""
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map {
        |      case (a, ${START}b$END) => a
        |    }
        |  }
        |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map {
        |      case (a, b) => a
        |    }
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map {
        |      case (a, _) => a
        |    }
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testUnusedRegularAnonymousFunction(): Unit = {
    val code =
      s"""
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map (${START}a$END => 1)
        |  }
        |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map (a => 1)
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map (_ => 1)
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testFor(): Unit = {
    val code =
      s"""
        |class Moo {
        |  val s = Seq("")
        |  for (${START}j$END <- s) {
        |    println(s)
        |  }
        |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  val s = Seq("")
        |  for (j <- s) {
        |    println(s)
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Moo {
        |  val s = Seq("")
        |  for (_ <- s) {
        |    println(s)
        |  }
        |}
      """.stripMargin
    testQuickFix(before, after, hint)
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
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testImplicitParamter(): Unit = {
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

  def testLocalClass(): Unit = {
    val code =
      s"""
         |class Person() {
         |  def func() = {
         |    object ${START}A$END
         |  }
         |}
      """.stripMargin
    val before =
      """
        |class Person() {
        |  def func() = {
        |    object A
        |  }
        |}
      """.stripMargin
    val after =
      """
        |class Person() {
        |  def func() = {
        |
        |  }
        |}
      """.stripMargin

    checkTextHasError(code)
    testQuickFix(before, after, hint)
  }

  def testInnerClass(): Unit = {
    val code =
      s"""
         |class Person() {
         |  private object ${START}A$END
         |}
      """.stripMargin
    val before =
      """
        |class Person() {
        |  private object A
        |}
      """.stripMargin
    val after =
      """
        |class Person() {
        |
        |}
      """.stripMargin

    checkTextHasError(code)
    testQuickFix(before, after, hint)
  }

  // SCL-17181
  def testOverridingSymbol(): Unit = checkTextHasNoErrors(
    """
      |trait A {
      |  val foo: Int
      |  val foo1: Int = foo + 1
      |}
      |val a: A = new A {
      |  override val foo: Int = 1 // marked as unused
      |}
      |""".stripMargin
  )

  // SCL-16919
  def testOverriddenSymbol(): Unit = checkTextHasNoErrors(
    """
      |class A {
      |  def foo(str: String): Unit = ()
      |}
      |class B extends A {
      |  override def foo(str: String): Unit = {
      |    println(str)
      |  }
      |}
      |""".stripMargin
  )

  def testPublicTypeMemberInPublicTrait(): Unit = checkTextHasNoErrors(
    """
      |trait Test {
      |  type ty = Int
      |}
      |""".stripMargin
  )

  def testPrivateTypeMemberInPublicTrait(): Unit = checkTextHasNoErrors(
    """
      |trait Test {
      |  private type ${START}ty$END = Int
      |}
      |""".stripMargin
  )


  def testPublictTypeMemberInPrivateTrait(): Unit = checkTextHasNoErrors(
    s"""
      |private trait Test {
      |  type ${START}ty$END = Int
      |}
      |""".stripMargin
  )

  def testOverridenTypeMember(): Unit = checkTextHasNoErrors(
    """
      |private trait Base {
      |  type ty
      |}
      |private class Test extends Base {
      |  override type ty = Int
      |}
      |""".stripMargin
  )

  def test_private_auxiliary_constructor_is_not_used(): Unit = checkTextHasError(
    s"""
       |class Test(val s: Int) {
       |  private def ${START}this$END() = this(3)
       |}
       |object Test {
       |  def foo() = new Test(3)
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
       |""".stripMargin
  )

  // SCL-17662
  def test_private_auxiliary_constructor_is_used_by_other_constructor(): Unit = checkTextHasNoErrors(
    s"""
       |class Test(val s: String, val i: Int) {
       |  private def this(s: String) = this(s, 42)
       |  def this(i: Int) = this(i.toString)
       |}
       |""".stripMargin
  )

  // SCL-18600
  def testUnusedAnnotation(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.annotation.unused
       |
       |private object Test {
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
       |private object Test {
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
