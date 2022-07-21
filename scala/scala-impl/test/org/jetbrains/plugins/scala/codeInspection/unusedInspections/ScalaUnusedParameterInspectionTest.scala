package org.jetbrains.plugins.scala.codeInspection.unusedInspections

class ScalaUnusedParameterInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  private val p = START + "p" + END

  val paramsPlaceholder = "<params-placeholder>"
  val argsPlaceholder = "<args-placeholder>"

  private def doParamTest(templ: String)(beforeClause: String, afterClause: String, argsBefore: String, argsAfter: String): Unit = {
    checkTextHasError(templ.replace(paramsPlaceholder, beforeClause).replace(argsPlaceholder, argsBefore), allowAdditionalHighlights = true)

    val rawArgsBefore = beforeClause.replace(START, "").replace(END, "")
    val before = templ.replace(paramsPlaceholder, rawArgsBefore).replace(argsPlaceholder, argsBefore)
    val after = templ.replace(paramsPlaceholder, afterClause).replace(argsPlaceholder, argsAfter)
    testQuickFix(before, after, removeUnusedElementHint)
  }

  private val doFunctionParameterTest = doParamTest(
    s"""
       |import scala.annotation.unused
       |@unused
       |class Foo {
       |  val a = 0
       |  val b = 0
       |  def test$paramsPlaceholder: Int = {
       |    // a, b should neither be unused nor unresolvable
       |    a + b
       |  }
       |
       |  val c = a + b
       |  test$argsPlaceholder
       |}
    """.stripMargin
  ) _

  private val doConstructorParameterTest = doParamTest(
    s"""
       |import scala.annotation.unused
       |@unused
       |class Foo {
       |  val a = 0
       |  val b = 0
       |  class Test$paramsPlaceholder {
       |    // a, b should neither be unused nor unresolvable
       |    a + b
       |  }
       |
       |  val c = a + b
       |  new Test$argsPlaceholder
       |}
    """.stripMargin
  ) _

  private def doTest(beforeClause: String, afterClause: String, argsBefore: String, argsAfter: String): Unit = {
    doFunctionParameterTest(beforeClause, afterClause, argsBefore, argsAfter)
    doConstructorParameterTest(beforeClause, afterClause, argsBefore, argsAfter)
  }

  def testMain(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  def main(args: Array[String]): Unit = ()
      |}
      |""".stripMargin
  )

  def testEasyLast(): Unit =
    doTest(s"(a: Int, $p: Int)", "(a: Int)",
           s"(1, 2)",            "(1)")

  def testEasyFirst(): Unit =
    doTest(s"($p: Int, a: Int)", "(a: Int)",
           s"(1, 2)",            "(2)")

  def testEmptyAfter(): Unit = {
    doTest(s"($p: Int)", "()",
           s"(1)",       "()")
  }

  def testMultipleClauses(): Unit =
    doTest(s"(a: Int, $p: Int)(b: Int)", "(a: Int)(b: Int)",
            "(1, 2)(3)",                 "(1)(3)")

  def testMultipleClausesEmptyAfter(): Unit =
    doTest(s"(a: Int)($p: Int)", "(a: Int)",
            "(1)(2)",            "(1)")


  def testMultipleClausesEmptyAfter2(): Unit =
    doTest(s"($p: Int)(a: Int)", "(a: Int)",
            "(1)(2)",            "(2)")

  def testUnusedParameterInCaseClass(): Unit = checkTextHasNoErrors(
    s"""
      |case class Test(a: Int)
      |Test(42)
      |""".stripMargin
  )

  def testCaseClassSndClause(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |@unused
       |case class Test(a: Int)($p: Int)""".stripMargin
  )

  ///////// normal class parameter /////////
  def testUnusedPrivateClass(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |@unused
       |object Global {
       |  @unused
       |  private class Test($p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClass(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |@unused
       |object Global {
       |  @unused
       |  class Test($p: Int)
       |}
       |""".stripMargin
  )

  ///////// val class parameter /////////
  def testUnusedPrivateClassVal(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |@unused
       |object Global {
       |  @unused
       |  private class Test(val $p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClassVal(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |@unused
       |object Global {
       |  @unused
       |  class Test(val $p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClassPrivateVal(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |@unused
       |object Global {
       |  @unused
       |  class Test(private val $p: Int)
       |}
       |""".stripMargin
  )

  def testUsedPrivateClassVal(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.annotation.unused
       |@unused
       |object Global {
       |  private class Test(val p: Int)
       |  val x = new Test(3)
       |  println(x.p)
       |}
       |""".stripMargin
  )


  ///////// case class parameter /////////
  def testUnusedPrivateCaseClass(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  private case class ${START}Test$END(a: Int)
       |}
       |Global
       |""".stripMargin
  )

  def testUnusedPublicCaseClass(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  case class ${START}Test$END(a: Int)
       |}
       |Global
       |""".stripMargin
  )

  def testUsedPrivateCaseClass(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.annotation.unused
       |@unused
       |object Global {
       |  private case class Test(p: Int)
       |  val x = new Test(3)
       |  println(x.p)
       |}
       |""".stripMargin
  )

  // inheritance stuff
  def testOverrideWithVal(): Unit = checkTextHasNoErrors(
    """
      |object Global {
      |  trait Base {
      |    def p: Int
      |  }
      |  private class Test(val p: Int) extends Base
      |  new Test(1)
      |}
      |Global
      |""".stripMargin
  )

  def testOverrideWithCaseClass(): Unit = checkTextHasNoErrors(
    """
      |object Global {
      |  trait Base {
      |    def p: Int
      |  }
      |  private case class Test(p: Int) extends Base
      |  Test(1) match { case _: Test => () }
      |}
      |Global
      |""".stripMargin
  )

  def testHighlightImplicitParameter(): Unit = checkTextHasError(
    s"""
      |import scala.annotation.unused
      |@unused
      |object Test {
      |  @unused
      |  def test(implicit ${START}unused$END: A): Unit = ()
      |}
      |""".stripMargin
  )

  /*private val doImplicitParameterTest = doParamTest(
    s"""
       |class Unused {
       |  trait T1
       |  trait T2
       |  val i = 0
       |  val j = 0
       |  private def test$paramsPlaceholder: Unit = {
       |    use()
       |    i + j
       |  }
       |
       |  def use()(implicit a: T1, b: T2): Unit = {
       |    a
       |    b
       |  }
       |
       |  {
       |    implicit val unused: Unused = new Unused
       |    test$argsPlaceholder
       |  }
       |}
    """.stripMargin
  )

  def testNoCalledImplicit(): Unit = doImplicitParameterTest(
    s"()(implicit $p: Unused)", "()",
    "()", "()"
  )

  def testLoneImplicitExplicitlyCalled(): Unit = doImplicitParameterTest(
    s"(implicit $p: Unused)", "()",
    "(new Unused)", "()"
  )


  def testImplicitWithMultipleArgs(): Unit = doImplicitParameterTest(
    s"(implicit $p: Unused, a: T1)", "(implicit a: T1)",
    "(new Unused, new T1)", "(new T1)"
  )

  def testImplicitWithMultipleArgs2(): Unit = doImplicitParameterTest(
    s"(implicit a: T1, $p: Unused)", "(implicit a: T1)",
    "(new T1, new Unused)", "(new T1)"
  )


  def testMultipleImplicitParamsWithNormalParams(): Unit = doImplicitParameterTest(
    s"(i: Int)(implicit $p: Unused, a: T1)", "(i: Int)(a: T1)",
    "(1)(new Unused, new T1)", "(1)(new T1)"
  )*/

  def testNotHighlightUsedImplicit(): Unit = checkTextHasNoErrors(
    """
      |import scala.annotation.unused
      |trait Used
      |@unused
      |object Test {
      |  implicit val used: Used = null
      |  test()
      |  private def test()(implicit used: Used): Unit = {
      |    test2()
      |  }
      |  private def test2()(implicit used: Used): Used = used
      |}
      |""".stripMargin
  )

  def testLocalFunctionParam(): Unit = checkTextHasError(
    s"""
      |import scala.annotation.unused
      |@unused
      |object Outer {
      |  @unused
      |  def f(): Any = {
      |    def g($p: List[Int]): Boolean = false
      |    g _
      |  }
      |}
      |""".stripMargin
  )

  def testLocalPublicClassParam(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |@unused
       |object PatternMatching {
       |  @unused
       |  def f(): Unit = {
       |    @unused
       |    class Test(val $p: Int)
       |    new Test(_)
       |  }
       |}
       |""".stripMargin
  )

  // SCL-18600
  def testUnusedAnnotation(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.annotation.unused
       |import scala.beans.BeanProperty
       |@unused
       |object Test {
       |  @unused type Units = Seq[Unit]
       |  @unused @BeanProperty var foo: Int = 42
       |  @unused def test(@unused param: Int): Unit = ()
       |  @unused private case class Test1(param: Int)
       |  @unused private class Test2(@unused param: Int)
       |  @unused private class Test3(@unused val param: Int)
       |}
       |""".stripMargin
  )

  def testParamInClassWithUnusedAnnotation(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |@unused
       |object Test {
       |  @unused
       |  private class Test($p: Int)
       |}
       |""".stripMargin
  )

  def testNoWarn(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.annotation.nowarn
       |
       |@nowarn("unused")
       |object Test {
       |  @nowarn("unused") def test(@nowarn("unused") param: Int): Unit = ()
       |  @nowarn("unused") private class Test2(@nowarn("unused") param: Int)
       |  @nowarn("unused") private class Test3(@nowarn("unused") val param: Int)
       |}
       |""".stripMargin
  )
}
