package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility

class ConstructorAnnotatorTest extends SimpleTestCase {
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
  class Z[+A]; object Z extends Z[Nothing];
  class Y[+A]
  class E[X](a: Z[X]) {
    def this(o: Y[X]) = this(Z)
  }
  class F(implicit a: Int)
  class Klass[K](a: K)
  type Alias[A] = Klass[A]
  new Alias("")
  val iAmAScriptFile = ()
  """ 
  
  def testEmpty() {
    assertMatches(messages("")) {
      case Nil =>
    }
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
      case Error("1", "Too many arguments for constructor(Int)") :: Nil =>
    }

    assertMessagesSorted(messages("new D(0, 1)"))(
      Error("1", "Too many arguments for constructor(Int)"),
      Error("1", "Too many arguments for constructor(Boolean)")
    )

    assertMessagesSorted(messages("new D(true, 1)"))(
      Error("1", "Too many arguments for constructor(Int)"),
      Error("1", "Too many arguments for constructor(Boolean)")
    )

    assertMessagesSorted(messages("new D(true, 1) {}"))(
      Error("1", "Too many arguments for constructor(Int)"),
      Error("1", "Too many arguments for constructor(Boolean)")
    )
  }

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
      Error("D", "Unspecified value parameters: b: Boolean"),
      Error("D", "Unspecified value parameters: a: Int")
    )

    assertMessagesSorted(messages("new D() {}"))(
      Error("()", "Unspecified value parameters: b: Boolean"),
      Error("()", "Unspecified value parameters: a: Int")
    )

    assertMessagesSorted(messages("new DD()"))(
      Error("()", "Unspecified value parameters: b: Boolean, c: Int"),
      Error("()", "Unspecified value parameters: a: Int")
    )

    assertMessagesSorted(messages("new DD()"))(
      Error("()", "Unspecified value parameters: b: Boolean, c: Int"),
      Error("()", "Unspecified value parameters: a: Int")
    )

    assertMessagesSorted(messages("new DD() {}"))(
      Error("()", "Unspecified value parameters: b: Boolean, c: Int"),
      Error("()", "Unspecified value parameters: a: Int")
    )
  }

  def testMissingAndTypeMismatch() {
    assertMessagesSorted(messages("new DD(true)"))(
      Error("(true)", "Unspecified value parameters: c: Int"),
      Error("true", "Type mismatch, expected: Int, actual: Boolean")
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
      Error("3.3", "Type mismatch, expected: Boolean, actual: Double"),
      Error("3.3", "Type mismatch, expected: Int, actual: Double")
    )

    assertMessagesSorted(messages("new D(3.3) {}"))(
      Error("3.3", "Type mismatch, expected: Boolean, actual: Double"),
      Error("3.3", "Type mismatch, expected: Int, actual: Double")
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
    val annotator = new ConstructorAnnotator {}
    val file: ScalaFile = (Header + code).parse

    val mock = new AnnotatorHolderMock(file)

    val seq = file.depthFirst().instanceOf[ScClass]
    Compatibility.seqClass = seq

    try {
      file.depthFirst().instancesOf[ScConstructor].foreach {
        annotator.annotateConstructor(_, mock)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}