package org.jetbrains.plugins.scala.codeInspection.unused

class ScalaUnusedParameterInspectionTest extends ScalaUnusedSymbolInspectionTestBase {

  private val p = START + "p" + END

  val paramsPlaceholder = "<params-placeholder>"
  val argsPlaceholder = "<args-placeholder>"

  private def doParamTest(templ: String)(beforeClause: String, afterClause: String, argsBefore: String, argsAfter: String): Unit = {
    checkTextHasError(templ.replace(paramsPlaceholder, beforeClause).replace(argsPlaceholder, argsBefore))

    val rawArgsBefore = beforeClause.replace(START, "").replace(END, "")
    val before = templ.replace(paramsPlaceholder, rawArgsBefore).replace(argsPlaceholder, argsBefore)
    val after = templ.replace(paramsPlaceholder, afterClause).replace(argsPlaceholder, argsAfter)
    testQuickFix(before, after, hint)
  }

  private val doFunctionParameterTest = doParamTest(
    s"""
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

  def testNotInCaseClass(): Unit = checkTextHasNoErrors(
    "case class Test(a: Int)"
  )

  def testCaseClassSndClause(): Unit = checkTextHasError(
    s"case class Test(a: Int)($p: Int)"
  )

  ///////// normal class parameter /////////
  def testUnusedPrivateClass(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  private class Test($p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClass(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  class Test($p: Int)
       |}
       |""".stripMargin
  )

  ///////// val class parameter /////////
  def testUnusedPrivateClassVal(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  private class Test(val $p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClassVal(): Unit = checkTextHasNoErrors(
    s"""
       |object Global {
       |  class Test(val p: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicClassPrivateVal(): Unit = checkTextHasError(
    s"""
       |object Global {
       |  class Test(private val $p: Int)
       |}
       |""".stripMargin
  )

  def testUsedPrivateClassVal(): Unit = checkTextHasNoErrors(
    s"""
       |object Global {
       |  private class Test(val p: Int)
       |  val x = new Test(3)
       |  println(x.p)
       |}
       |""".stripMargin
  )


  ///////// case class parameter /////////
  def testUnusedPrivateCaseClass(): Unit = checkTextHasNoErrors(
    s"""
       |object Global {
       |  private case class Test(a: Int)
       |}
       |""".stripMargin
  )

  def testUnusedPublicCaseClass(): Unit = checkTextHasNoErrors(
    s"""
       |object Global {
       |  case class Test(p: Int)
       |}
       |""".stripMargin
  )

  def testUsedPrivateCaseClass(): Unit = checkTextHasNoErrors(
    s"""
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
      |}
      |""".stripMargin
  )

  def testOverrideWithCaseClass(): Unit = checkTextHasNoErrors(
    """
      |object Global {
      |  trait Base {
      |    def p: Int
      |  }
      |  private case class Test(p: Int) extends Base
      |}
      |""".stripMargin
  )

  // implicit stuff
  def testDontHighlightImplicitParameter(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  def test(implicit unused: A): Unit = ()
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
      |trait Used
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

  def testNoHighlightInnerPrivateCaseClassParam(): Unit = checkTextHasNoErrors(
    s"""
      |object Test {
      |  private object Inner {
      |    case class CC(a: Int)
      |  }
      |
      |  Inner
      |}
      |""".stripMargin
  )

  def testHighlightCaseClassParamInPrivateTopLevelObject(): Unit = checkTextHasNoErrors(
    s"""
       |private object Test {
       |  case class CC(a: Int)
       |}
       |""".stripMargin
  )

  def testDontHighlightParametersInDeclarations(): Unit = checkTextHasNoErrors(
    """
      |trait Base {
      |  def test(i: Int): Unit
      |}
      |""".stripMargin
  )

  def testDontHighlightUnusedParametersInOverridingMethod(): Unit = checkTextHasNoErrors(
    """
      |trait Base {
      |  def test(i: Int): Unit
      |}
      |
      |class Test extends Base {
      |  def test(i: Int): Unit = ()
      |}
      |""".stripMargin
  )

  def testLocalFunctionParam(): Unit = checkTextHasError(
    s"""
      |object Outer {
      |  def f(): Any = {
      |    def g($p: List[Int]): Boolean = false
      |    g _
      |  }
      |}
      |""".stripMargin
  )

  def testLocalPublicClassParam(): Unit = checkTextHasError(
    s"""
       |object PatternMatching {
       |  def f(): Unit = {
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
       |
       |object Test {
       |  def test(@unused param: Int): Unit = ()
       |  private case class Test1(@unused param: Int)
       |  private class Test2(@unused param: Int)
       |  private class Test3(@unused val param: Int)
       |}
       |""".stripMargin
  )

  def testParamInClassWithUnusedAnnotation(): Unit = checkTextHasError(
    s"""
       |import scala.annotation.unused
       |
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
       |object Test {
       |  def test(@nowarn("unused") param: Int): Unit = ()
       |  private case class Test1(@nowarn("unused") param: Int)
       |  private class Test2(@nowarn("unused") param: Int)
       |  private class Test3(@nowarn("unused") val param: Int)
       |}
       |""".stripMargin
  )
}
