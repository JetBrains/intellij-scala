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
         |new Foo
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Foo {
        |  private val s = 0
        |}
        |new Foo
      """.stripMargin
    val after =
      """
        |class Foo {
        |}
        |new Foo
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
         |Foo.foo()
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |object Foo {
        |  def foo(): Unit = {
        |    val s = 0
        |  }
        |}
        |Foo.foo()
      """.stripMargin
    val after =
      """
        |object Foo {
        |  def foo(): Unit = {
        |  }
        |}
        |Foo.foo()
      """.stripMargin
    testQuickFix(before, after, hint)
  }

  def testNonPrivateField(): Unit = {
    val code =
      s"""
        |class Foo {
        |  val ${START}s$END: String = ""
        |  protected val ${START}z$END: Int = 2
        |}
        |new Foo
      """.stripMargin
    checkTextHasError(code)
  }

  def testRemoveMultiDeclaration(): Unit = {
    val code =
      s"""
         |class Foo {
         |  private val (${START}a$END, b): String = ???
         |  println(b)
         |}
         |new Foo
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Foo {
        |  private val (a, b): String = ???
        |  println(b)
        |}
        |new Foo
      """.stripMargin
    val after =
      """
        |class Foo {
        |  private val (_, b): String = ???
        |  println(b)
        |}
        |new Foo
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
        |new Bar().aa()
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
         |new Bar().aa()
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
         |new Bar().aa()
      """.stripMargin
    val after =
      s"""
         |class Bar {
         |  def aa(): Unit = {
         |    var (_, a) = 10
         |    println(a)
         |  }
         |}
         |new Bar().aa()
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
         |new Moo
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
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(_: String) =>
        |      println("AA")
        |  }
        |}
        |new Moo
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
         |new Moo
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
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(_) =>
        |      println("AA")
        |  }
        |}
        |new Moo
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
         |new Moo
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  Option("").map {
        |    case a: String =>
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option("").map {
        |    case _: String =>
        |  }
        |}
        |new Moo
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
         |new Moo
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(a) => println(a)
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case Some(a) => println(a)
        |  }
        |}
        |new Moo
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
         |new Moo
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(a) => println(s)
        |  }
        |}
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  Option(null) match {
        |    case s@Some(_) => println(s)
        |  }
        |}
        |new Moo
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
        |new Moo().foo(Seq.empty)
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
        |new Moo().foo(Seq.empty)
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
        |new Moo().foo(Seq.empty)
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
        |new Moo().foo(Seq.empty)
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map (a => 1)
        |  }
        |}
        |new Moo().foo(Seq.empty)
      """.stripMargin
    val after =
      """
        |class Moo {
        |  def foo(s: Seq[(Int, Int)]): Seq[Int] = {
        |    s.map (_ => 1)
        |  }
        |}
        |new Moo().foo(Seq.empty)
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
        |new Moo
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
        |new Moo
      """.stripMargin
    val after =
      """
        |class Moo {
        |  val s = Seq("")
        |  for (_ <- s) {
        |    println(s)
        |  }
        |}
        |new Moo
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

  def testLocalClass(): Unit = {
    val code =
      s"""
         |class Person() {
         |  def func() = {
         |    object ${START}A$END
         |  }
         |}
         |new Person().func()
      """.stripMargin
    val before =
      """
        |class Person() {
        |  def func() = {
        |    object A
        |  }
        |}
        |new Person().func()
      """.stripMargin
    val after =
      """
        |class Person() {
        |  def func() = {
        |
        |  }
        |}
        |new Person().func()
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
         |new Person
      """.stripMargin
    val before =
      """
        |class Person() {
        |  private object A
        |}
        |new Person
      """.stripMargin
    val after =
      """
        |class Person() {
        |
        |}
        |new Person
      """.stripMargin

    checkTextHasError(code)
    testQuickFix(before, after, hint)
  }

  // SCL-17181
  def testOverridingSymbol(): Unit = checkTextHasNoErrors(
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
  def testOverriddenSymbol(): Unit = checkTextHasNoErrors(
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

  def testPublicTypeMemberInPublicTrait(): Unit = checkTextHasError(
    s"""
      |trait Test {
      |  type ${START}ty$END = Int
      |}
      |object Test extends Test
      |Test
      |""".stripMargin
  )

  def testPrivateTypeMemberInPublicTrait(): Unit = checkTextHasError(
    s"""
      |trait Test {
      |  private type ${START}ty$END = Int
      |}
      |object Test extends Test
      |Test
      |""".stripMargin
  )


  def testPublicTypeMemberInPrivateTrait(): Unit = checkTextHasError(
    s"""
      |private trait Test {
      |  type ${START}ty$END = Int
      |}
      |object Test extends Test
      |Test
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

  def test_private_auxiliary_constructor_is_not_used(): Unit = checkTextHasError(
    s"""
       |class Test(val s: Int) {
       |  private def ${START}this$END() = this(s)
       |}
       |object Test {
       |  def foo() = new Test(3)
       |}
       |Test.foo()
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

  def testSideEffectDefinition(): Unit = {
    val text =
      """
        |object Test {
        |  def test(): Int = 3
        |  private val x = test()
        |}
        |""".stripMargin

    testQuickFix(
      text,
      """
        |object Test {
        |  def test(): Int = 3
        |}
        |""".stripMargin,
      hintWholeDefinition
    )

    testQuickFix(
      text,
      """
        |object Test {
        |  def test(): Int = 3
        |
        |  test()
        |}
        |""".stripMargin,
      hintOnlyXBinding
    )
  }

  def testSugarOp(): Unit = checkTextHasNoErrors(
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

  def testPropertyAssign_used(): Unit = checkTextHasNoErrors(
    s"""object Test {
       |  private def data: Int = 0
       |  private def data_=(i: Int): Int = i
       |
       |  Test.data = 3
       |}
       |""".stripMargin
  )

  def testPropertyAssign_unused(): Unit = checkTextHasError(
    s"""object Test {
       |  private def data: Int = 0
       |  private def ${START}data_=$END(i: Int): Int = i
       |
       |  println(Test.data)
       |}
       |""".stripMargin
  )

  def testAppEntryPoint(): Unit = checkTextHasNoErrors(
    "object Foo extends App"
  )

  def testMainMethodEntryPoint(): Unit = checkTextHasNoErrors(
    "object Foo { def main(thisNameCanBeAnything: Array[String]) = {} }"
  )
}
