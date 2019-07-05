package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScConstructorInvocationAnnotator
import org.jetbrains.plugins.scala.base.AssertMatches
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility

class ConstructorInvocationAnnotatorTest extends AnnotatorSimpleTestCase {
  final val Header = """
  class Seq[+A] 
  object Seq { def apply[A](a: A) = new Seq[A] }
  class Simple
  class Complex(r: Double, i: Double)
  class A(a: Int)
  class B[X](a: X)
  class C(a: Int) {
    def this() = this(0)
  }
  class D(a: Int) {
    def this(b: Boolean) = this(0)
  }
  class DD(a: Int) {
    def this(b: Boolean, c: Int) = this(0)
  }
  class DDD(a: Int)(b: Int)
  class DDD2(a: Int) {
    def this(b: Boolean)(c: Boolean) = this(0)
  }
  class FFF[X](a: X)(b: X)
  class GGG[X, Y](a: X)(b: Y)
  class Z[+A];
  class Y[+A]
  class E[X](a: Z[X]) {
    def this(o: Y[X]) = this(Z)
  }
  class EE
  class F(implicit a: Int)
  class Klass[K](a: K)
  type Alias[A] = Klass[A]
  """
  
  def testEmpty() {
    assertNothing(messages(""))
  }
  
  def testFine() {
    val codes = Seq(
      "new Simple",
      "new Simple()",
      "new Complex(1.0, 1.0)",
      "new A(0)",
      "new A(a = 0)",
      "new B[Int](0)",
      "new B(0)",
      "new C(0)",
      "new C()",
      "new C",
      "new D(0)",
      "new D(false)",
      "new DD(0)",
      "new DD(false, 1)",
      "new DDD(1)(2)",
      "new FFF(1)(2)",
      "new GGG(1)(true)",
      "new E[Int](new Y[Int])",
      "new E[Int](new Z[Int])",
      "new E(new Y[Int])",
      "new E(new Z[Int])",
      "new Alias[Int](0)"
    )
    for {code <- codes} {
      assertNothing(messages(code))
      assertNothing(messages(code + " {}"))
    }
  }

  def testExcessArguments() {
    assertMatches(messages("new A(0, 1)")) {
      case Error(", 1", "Too many arguments for constructor(Int)") :: Nil =>
    }

    assertMessagesSorted(messages("new D(0, 1)"))(
      Error("D", "Cannot resolve overloaded constructor `D`") // SCL-15594
    )

    assertMessagesSorted(messages("new D(true, 1)"))(
      Error("D", "Cannot resolve overloaded constructor `D`") // SCL-15594
    )

    assertMessagesSorted(messages("new D(true, 1) {}"))(
      Error("D", "Cannot resolve overloaded constructor `D`") // SCL-15594
    )

    assertMessagesSorted(messages("new DDD(1)(2, 3)"))(
      Error(", 3", "Too many arguments for constructor(Int)(Int)")
    )
  }

  def testAutoTupling(): Unit = {
    assertNothing(messages("new B"))
    assertNothing(messages("new B()"))
    assertNothing(messages("new FFF()()"))
    assertNothing(messages("new GGG()()"))
    assertMessages(messages("new FFF()(true)"))(
      Error("true", "Type mismatch, expected: Unit, actual: Boolean")
    )
  }

  // TODO Don't separate the code from the expected messages (it's hard to understand such a test)
  def testMissedParameters() {
    assertMatches(messages("new A")) {
      case Error(_, "Unspecified value parameters: a: Int") :: Nil =>
    }
    assertMatches(messages("new A()")) {
      case Error(_, "Unspecified value parameters: a: Int") :: Nil =>
    }
    assertMatches(messages("new B[Int]()")) {
      case Error(_, "Unspecified value parameters: a: Int") :: Nil =>
    }

    assertMessagesSorted(messages("new D"))(
      Error("D", "Cannot resolve overloaded constructor `D`") // SCL-15594
    )

    assertMessagesSorted(messages("new D() {}"))(
      Error("D", "Cannot resolve overloaded constructor `D`") // SCL-15594
    )

    assertMessagesSorted(messages("new DD()"))(
      Error("DD", "Cannot resolve overloaded constructor `DD`") // SCL-15594
    )

    assertMessagesSorted(messages("new DD()"))(
      Error("DD", "Cannot resolve overloaded constructor `DD`") // SCL-15594
    )

    assertMessagesSorted(messages("new DD() {}"))(
      Error("DD", "Cannot resolve overloaded constructor `DD`") // SCL-15594
    )

    assertMessagesSorted(messages("new DDD(3)()"))(
      Error("()", "Unspecified value parameters: b: Int")
    )

    assertMessagesSorted(messages("new DDD()()"))(
      Error("()", "Unspecified value parameters: a: Int"),
      Error("()", "Unspecified value parameters: b: Int")
    )
  }

  def testMissingArgumentClause(): Unit = {
    assertMessagesSorted(messages("new DDD(3)"))(
      Error(")", "Missing argument list for constructor(Int)(Int)")
    )

    assertMessagesSorted(messages("new DDD2(true)"))(
      Error(")", "Missing argument list for constructor(Boolean)(Boolean)")
    )
  }

  def testMissingArgumentClauseWithImplicit(): Unit = {
    assertMessagesSorted(messages("class Test()(implicit impl: Test); new Test()")) (
      Error("Test()", "No implicit arguments of type: Test")
    )
    assertMessagesSorted(messages("class Test()(private implicit impl: Test); new Test()")) (
      Error("Test()", "No implicit arguments of type: Test")
    )
  }

  def testMissingAndTypeMismatch() {
    assertMessagesSorted(messages("new DD(true)"))(
      Error("DD", "Cannot resolve overloaded constructor `DD`") // SCL-15594
    )
  }



  def testPositionalAfterNamed() {
    assertMatches(messages("new Complex(i = 1.0, 5.0)")) {
      case Error("5.0", "Positional after named argument") :: Nil =>
    }

    assertMatches(messages("new Complex(i = 1.0, 5.0) {}")) {
      case Error("5.0", "Positional after named argument") :: Nil =>
    }
  }

  def testNamedDuplicates() {
    assertMessagesSorted(messages("new A(a = null, a = Unit)"))(
      Error("a", "Parameter specified multiple times"),
      Error("a", "Parameter specified multiple times")
    )

    assertMessagesSorted(messages("new A(a = null, a = Unit) {}"))(
      Error("a", "Parameter specified multiple times"),
      Error("a", "Parameter specified multiple times")
    )
  }


  def testSinglePrivateConstructorIsInaccessible(): Unit = {
    val text =
      """
        |class P private(a: Int)
        |
        |new P()
      """.stripMargin

    assertMatches(messages(text)) {
      case Error("()", "No constructor accessible from here") :: Nil =>
    }

    assertMatches(messages(text + " {}")) {
      case Error("()", "No constructor accessible from here") :: Nil =>
    }
  }


  def testMultiplePrivateConstructorsAreInaccessible(): Unit = {
    val text =
      """
        |class P private(a: Int) {
        |  private def this(a: Boolean) = this(???)
        |}
        |
        |new P(3)
      """.stripMargin

    assertMatches(messages(text)) {
      case Error("(3)", "No constructor accessible from here") :: Nil =>
    }

    assertMatches(messages(text + " {}")) {
      case Error("(3)", "No constructor accessible from here") :: Nil =>
    }
  }


  def testPrivatePrimaryConstructorIsIgnored(): Unit = {
    val text =
      """
        |class P private (a: Int) {
        |  def this(a: Boolean) = this(3)
        |}
        |
        |new P(3)
      """.stripMargin

    assertMatches(messages(text)) {
      case Error("3", "Type mismatch, expected: Boolean, actual: Int") :: Nil =>
    }

    assertMatches(messages(text + " {}")) {
      case Error("3", "Type mismatch, expected: Boolean, actual: Int") :: Nil =>
    }
  }

  def testPrivateSecondaryConstructorIsIgnored(): Unit = {
    val text =
      """
        |class P(a: Int) {
        |  private def this(a: Boolean) = this(???)
        |}
        |
        |new P(true)
      """.stripMargin

    assertMatches(messages(text)) {
      case Error("true", "Type mismatch, expected: Int, actual: Boolean") :: Nil =>
    }

    assertMatches(messages(text + " {}")) {
      case Error("true", "Type mismatch, expected: Int, actual: Boolean") :: Nil =>
    }
  }

  def testTypeMismatch() {
    assertMatches(messages("new A(false)")) {
      case Error("false", "Type mismatch, expected: Int, actual: Boolean") :: Nil =>
    }

    assertMatches(messages("new B[Int](false)")) {
      case Error("false", "Type mismatch, expected: Int, actual: Boolean") :: Nil =>
    }

    assertMessagesSorted(messages("new D(3.3)"))(
      Error("D", "Cannot resolve overloaded constructor `D`") // SCL-15594
    )

    assertMessagesSorted(messages("new D(3.3) {}"))(
      Error("D", "Cannot resolve overloaded constructor `D`")// SCL-15594
    )

    assertMessagesSorted(messages("new DDD(true)(false)"))(
      Error("true", "Type mismatch, expected: Int, actual: Boolean") // SCL-15592
    )

    assertMessagesSorted(messages("new FFF(3)(true)"))(
      Error("true", "Type mismatch, expected: Int, actual: Boolean"),
    )
  }
  
  def testMalformedSignature() {
    assertMatches(messages("class Malformed(a: A*, b: B); new Malformed(0)")) {
      case Error("Malformed", "Constructor has malformed definition") :: Nil =>
    }
  }

  def testTraitInstantiation(): Unit = {
    val code =
      """
        |trait T
        |new T {}
      """.stripMargin

    assertNothing(messages(code))
  }

  def testTraitInstantiationWithSingleEmptyParameterList(): Unit = {
    val code =
      """
        |trait T
        |new T() {}
      """.stripMargin

    assertNothing(messages(code))
  }

  def testTraitInstantiationWithNonExistingConstructor(): Unit = {
    val code =
      """
        |trait T
        |new T(2, 2) {}
      """.stripMargin

    assertMessages(messages(code))(
      Error("(2, 2)", "T is a trait and thus has no constructor")
    )
  }

  def testMissingParamaterLists(): Unit = {
    val code =
      """
        |new DDD(2)
      """.stripMargin

    assertMessages(messages(code))(
      Error(")", "Missing argument list for constructor(Int)(Int)")
    )
  }

  // TODO: Type Aliases
  //class A(a: Int)
  //class B[X](a: X)
  //
  //type AA[A] = A[A]
  //type BB[A] = B[A]
  //new AA(0)
  //new BB(0)
  //new AA[Int](0)


  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val file: ScalaFile = (Header + code).parseWithEventSystem

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    val seq = file.depthFirst().instanceOf[ScClass]
    Compatibility.seqClass = seq

    try {
      file.depthFirst().instancesOf[ScConstructorInvocation].foreach {
        ScConstructorInvocationAnnotator.annotate(_)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}

class JavaConstructorInvocationAnnotatorTest extends ScalaHighlightingTestBase with AssertMatches {
  val javaCode =
    """
      |public class JavaClass {
      |  private final int x;
      |  private final int y;
      |  public JavaClass(int x, int y) {
      |    this.x = x;
      |    this.y = y;
      |  }
      |}
    """.stripMargin

  private def setup(): Unit = {
    getFixture.configureByText("JavaClass.java", javaCode)
  }

  def messages(scalaText: String): List[Message] = {
    setup()
    errorsFromScalaCode(scalaText)
  }

  def test_SCL15398_ok_new(): Unit = {
    assertNothing(messages("new JavaClass(1, 2)"))
  }

  def test_SCL15398_ok_extends(): Unit = {
    assertNothing(messages("class Impl extends JavaClass(1, 2)"))
  }

  def test_SCL15398_missing_parameter_new(): Unit = {
    assertMessages(messages("new JavaClass(1)"))(
      Error("(1)", "Unspecified value parameters: y: Int")
    )
  }

  def test_SCL15398_missing_parameter_extends(): Unit = {
    assertMessages(messages("class Impl extends JavaClass(1)"))(
      Error("(1)", "Unspecified value parameters: y: Int")
    )
  }
}
