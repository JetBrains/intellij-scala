package org.jetbrains.plugins.scala.annotator
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message.Error

abstract class ApplicationAnnotatorTest_Common extends ApplicationAnnotatorTestBase {
  import Message._

  def testEmpty(): Unit = {
    assertNoErrors("()")
  }

  def testFine(): Unit = {
    assertMatches(messages("def f(p: Any) {}; f(null)")) {
      case Nil =>
    }
  }

  def testDoesNotTakeParameters_1(): Unit = {
    assertMatches(messages("def f {}; f(Unit, null)")) {
      case Error("(Unit, null)", "f does not take parameters") :: Nil =>
    }
  }

  def testDoesNotTakeParameters_2(): Unit = {
    assertMatches(messages("def foo0: Int = 42 ; foo0()")) {
      case Error("()", "foo0 does not take parameters") :: Nil =>
    }
  }

  def testMissedParametersClause(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any) {}; f")) {
      case Error("f", "Missing arguments for method f(Any, Any)") :: Nil =>
    }
  }

  def testExcessArguments(): Unit = {
    assertMatches(messages("def f() {}; f(null)")) {
      case Error("(n", "Too many arguments for method f") :: Nil =>
    }

    assertMatches(messages("def f() {}; f(null, Unit)")) {
      case Error("(n", "Too many arguments for method f") :: Nil =>
    }

    assertMatches(messages("def f(p: Any) {}; f(null, Unit)")) {
      case Error(", U", "Too many arguments for method f(Any)") :: Nil =>
    }

    assertMatches(messages("def f(p: Any) {}; f(null, Unit, 123)")) {
      case Error(", U", "Too many arguments for method f(Any)") :: Nil =>
    }
  }

  // TODO "argument(s)" not "parameter(s)"
  def testMissedParameters(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any) {}; f()")) {
      case Error("()", "Unspecified value parameters: a: Any, b: Any") ::Nil =>
    }
  }

  def testMissedParametersWhitespace(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any) {}; f( )")) {
      case Error("( )", "Unspecified value parameters: a: Any, b: Any") ::Nil =>
    }
  }

  def testMissedOneParameter(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any) {}; f(123)")) {
      case Error("3)", "Unspecified value parameters: b: Any") ::Nil =>
    }
  }

  def testMissedMoreParameters(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any, c: Any) {}; f(123)")) {
      case Error("3)", "Unspecified value parameters: b: Any, c: Any") ::Nil =>
    }
  }

  def testMissedParameterWhitespace(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any) {}; f(123 )")) {
      case Error("3 )", "Unspecified value parameters: b: Any") ::Nil =>
    }
  }

  def testMissedParameterComma(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any) {}; f(123, )")) {
      case Error("3, )", "Unspecified value parameters: b: Any") ::Nil =>
    }
  }

  def testMissedParameterComment(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any) {}; f(123 /* foo */ )")) {
      case Error("3 /* foo */ )", "Unspecified value parameters: b: Any") ::Nil =>
    }
  }

  def testPositionalAfterNamed(): Unit = {
    assertMatches(messages("def f(a: Any, b: Any, c: Any) {}; f(c = null, null, Unit)")) {
      case Error("null", "Positional after named argument") ::
        Error("Unit", "Positional after named argument") :: Nil =>
    }
  }

  def testNamedDuplicates(): Unit = {
    assertMessagesSorted(messages("def f(a: Any) {}; f(a = null, a = Unit)"))(
      Error("a", "Parameter specified multiple times"),
      Error("a", "Parameter specified multiple times"),
    )
  }

  def testUnresolvedParameter(): Unit = {
    assertMessagesSorted(messages("def f(a: Any) {}; f(b = null)"))(
      Error("b", "Cannot resolve symbol b")
    )
  }

  def testTypeMismatch(): Unit = {
    assertMatches(messages("def f(a: A, b: B) {}; f(B, A)")) {
      case Error("B", "Type mismatch, expected: A, actual: B.type") :: Nil => // SCL-15592
    }
  }

  def testMalformedSignature(): Unit = {
    assertMessagesSorted(messages("def f(a: A*, b: B) {}; f(A, B)"))(
      Error("f", "'f' has malformed definition"),
      Error("a: A*", "*-parameter must come last")
    )
  }

  def testIncorrectExpansion(): Unit = {
    assertMatches(messages(
      """def f(a: Any, b: Any): Unit = {}
        |f(Seq(null): _*, Seq(null): _*)
        |""".stripMargin)) {
      case Error("Seq(null): _*", "Expansion for non-repeated parameter") ::
        Error("Seq(null): _*", "Expansion for non-repeated parameter") :: Nil =>
    }
  }

  def testDoesNotTakeTypeParameters(): Unit = {
    assertMatches(messages("def f = 0; f[Any]")) {
      case Error("[Any]", "method f does not take type arguments") :: Nil =>
    }
  }

  def testMissingTypeParameter(): Unit = {
    assertMatches(messages("def f[A, B] = 0; f[Any]")) {
      case Error("y]", "Unspecified type parameters: B") :: Nil =>
    }
  }

  def testExcessTypeParameter(): Unit = {
    assertMatches(messages("def f[A] = 0; f[Any, Any]")) {
      case Error(", A", "Too many type arguments for method f, expected: 1, found: 2") :: Nil =>
    }
  }

  def testNonApplicable_Object(): Unit = {
    assertMatches(messages("object Test; Test()")) {
      case Error("()", "'Test.type' does not take parameters") :: Nil =>
    }
  }

  def testNonApplicable_Int(): Unit = {
    assertMatches(messages("3()")) {
      case Error("()", "'Int' does not take parameters") :: Nil =>
    }
  }

  def testNonApplicable_Val(): Unit = {
    assertMatches(messages("val a: Any = (); a(3)")) {
      case Error("(3)", "'a.type' does not take parameters") :: Nil =>
    }
  }

  def testNonApplicableInUpdate(): Unit = {
    assertMatches(messages("object Test; Test(3) = 3")) {
      case Error("(3)", "'Test.type' does not take parameters") :: Nil =>
    }
  }

  def testNonApplicableOverloadedApply(): Unit = {
    val code =
      """
        |object Test {
        |  def apply(int: Int): Unit = ()
        |  def apply(str: Float): Unit = ()
        |}
        |Test(true)
      """.stripMargin

    assertMessagesSorted(messages(code))(
      Error("Test", "Cannot resolve overloaded method 'Test'") // SCL-15594
    )
  }

  def testNonApplicableOverloadedApplyFromDef(): Unit = {
    val code =
      """
        |class Test {
        |  def apply(int: Int): Unit = ()
        |  def apply(str: Float): Unit = ()
        |}
        |def test: Test = new Test
        |test(true)
      """.stripMargin

    assertMessagesSorted(messages(code))(
      Error("test", "Cannot resolve overloaded method 'test'") // SCL-15594
    )
  }

  def testNonApplicableOverloadedApplyFromFuncWithMultipleArgLists(): Unit = {
    val code =
      """
        |class Test {
        |  def apply(int: Int): Unit = ()
        |  def apply(str: Float): Unit = ()
        |}
        |def test(i: Int)(j: Int): Test = new Test
        |test(3)(3)(true)
      """.stripMargin

    assertMessagesSorted(messages(code))(
      Error("test(3)(3)", "Cannot resolve overloaded method") // SCL-15594
    )
  }


  def testDoubleDefinedUpdate(): Unit = {
    val code =
      """
        |object Test {
        |  protected def update(propName: Boolean, p: Any): Unit = ()
        |  def update(propName: Boolean, p: => Any): Unit = ()
        |}
        |Test(false) = true
      """.stripMargin

    assertNoErrors(code)
  }

  val fooDef = "def foo(first: Boolean, int: Int = 3, last: Boolean): Unit = ()\n"
  def assertWithFoo(code: String)(expected: Message*): Unit =
    assertMessagesSorted(messages(fooDef + code))(expected: _*)

  def testIncompleteCallWithNamedParam_1(): Unit =
    assertWithFoo("foo(last = )")(
      Error("= )", "Unspecified value parameters: first: Boolean")
    )

  def testIncompleteCallWithNamedParam_2(): Unit =
    assertWithFoo("foo(first = true, )")(
      Error("e, )", "Unspecified value parameters: last: Boolean")
    )

  def testIncompleteCallWithNamedParam_3(): Unit =
    assertWithFoo("foo(first = true, last = 3)")(
      Error("3", "Type mismatch, expected: Boolean, actual: Int")
    )

  def testIncompleteCallWithNamedParam_4(): Unit =
    assertWithFoo("foo(last = true, int = 34)")(
      Error("4)", "Unspecified value parameters: first: Boolean")
    )

  def testIncompleteCallWithNamedParam_5(): Unit =
    assertWithFoo("foo(last = true, 3, first = true)")(
      Error("3", "Positional after named argument")
    )

  def testSCL7021(): Unit = {
    assertMatches(messages(
      """trait Base {
        |  def foo(default: Int = 1): Any
        |}
        |
        |object Test {
        |  private val anonClass = new Base() {
        |    def foo(default: Int): Any = ()
        |  }
        |
        |  anonClass.foo()
        |}""".stripMargin
    )) {
      case Nil =>
    }
  }

  def testSCL10608(): Unit = {
    assertMatches(messages(
      """
        |def abc = {
        |    23
        |    val b = "nope"
        |  }
        |def foo(par: Int) = par
        |foo(abc)
      """.stripMargin
    )) {
      case Error("abc", "Type mismatch, expected: Int, actual: Unit") :: Nil =>
    }
  }


  protected val EmptyTrailingParametersClausesCode =
    """def foo1() = null
      |def foo2()() = null
      |def foo3()()() = null
      |def foo4(x: Int)()() = null
      |def foo5()()(x: Int) = null
      |
      |//SHOULD BE VALID
      |foo1
      |foo1()
      |
      |foo2
      |foo2()
      |foo2()()
      |
      |foo3
      |foo3()
      |foo3()() //OK: 2.13, ERROR: 2.12
      |foo3()()()
      |
      |foo4(42)
      |foo4(42)()
      |foo4(42)()()
      |""".stripMargin

  def testEmptyTrailingParametersClauses(): Unit = {
    assertNoErrors(EmptyTrailingParametersClausesCode)
  }

  def testEmptyLeadingParametersClauses(): Unit = {
    assertMessagesText(
      """def foo5()()(x: Int) = null
        |foo5(42)
        |foo5()(42)
        |foo5()()(42)
        |""".stripMargin,
      """Error(),Missing argument list () for method foo5()()(Int))
        |Error(),Missing argument list (x: Int) for method foo5()()(Int))
        |Error((4,Too many arguments for method foo5()()(Int))
        |Error((4,Too many arguments)
        |""".stripMargin
    )
  }
}

class ApplicationAnnotatorTest_2_11 extends ApplicationAnnotatorTest_Common {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_2_12
}

class ApplicationAnnotatorTest_2_12 extends ApplicationAnnotatorTest_Common {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_2_12
}

class ApplicationAnnotatorTest_2_13 extends ApplicationAnnotatorTest_Common {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_2_13
}

class ApplicationAnnotatorTest_3 extends ApplicationAnnotatorTest_Common {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3

  override def testEmptyTrailingParametersClauses(): Unit = {
    //TODO: this test has wrong expected test data
    // In Scala 3 empty arguments are mandatory and their absense is considered to be an error
    // Right now there is an inspection
    // `org.jetbrains.plugins.scala.codeInspection.methodSignature.ParameterlessAccessInspection.EmptyParenMethod`
    // which produces warning for Scala 2 but it should be an error in Scala 3
    // (See SCL-19504 for the details)
    // We might consider rewriting it as Annotator, or disabling it in Scala 2 and implementing similar logic in annotator for Scala 3
    // This is something to be discussed
    assertMessagesText(EmptyTrailingParametersClausesCode, "")
  }

  override def testNonApplicable_Int(): Unit = {
    assertMatches(messages("3()")) {
      case Error("()", "'3' does not take parameters") :: Nil =>
    }
  }
}
