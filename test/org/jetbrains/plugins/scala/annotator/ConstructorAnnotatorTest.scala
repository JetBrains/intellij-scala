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
  class A(a: Int)
  class B[X](a: X)
  class C(a: Int) {
    def this() = this(0)
  }
  class D(a: Int) {
    def this(a: Boolean) = this(0)
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
      "new A(0)",
      "new A(a = 0)",
      "new B[Int](0)}",
      "new B(0)",
      "new C(0)",
      "new C()",
      "new C",
      "new D(0)",
      "new D(false)",
      "new E[Int](new Y[Int])",
      "new E[Int](new Z[Int])",
      "new E(new Y[Int])",
      "new E(new Z[Int])",
      "new Alias[Int](0)"
    )
    for {code <- codes} {
      assertMatches(messages(code)) {
        case Nil =>
      }
    }
  }

  def testExcessArguments() {
    assertMatches(messages("new A(0, 1)")) {
      case Error("1", "Too many arguments for constructor") :: Nil =>
    }
  }

  def testMissedParameters() {
    assertMatches(messages("new A")) {
      case Error(_, "Unspecified value parameters: a: Int") :: Nil =>
    }
    assertMatches(messages("new A()")) {
      case Error(_, "Unspecified value parameters: a: Int") :: Nil =>
    }
    assertMatches(messages("new B[Int]()")) {
      case Error(_, "Unspecified value parameters: a: X") :: Nil =>
    }
  }

  def testNamedDuplicates() {
    assertMatches(messages("new A(a = null, a = Unit)")) {
      case Error("a", "Parameter specified multiple times") :: 
              Error("a", "Parameter specified multiple times") :: Nil =>
    }
  }
  
  def testTypeMismatch() {
    assertMatches(messages("new A(false)")) {
      case Error("false", "Type mismatch, expected: Int, actual: Boolean") :: Nil =>
    }
    assertMatches(messages("new B[Int](false)")) {
      case Error("false", "Type mismatch, expected: Int, actual: Boolean") :: Nil =>
    }
  }
  
  def testMalformedSignature() {
    assertMatches(messages("class Malformed(a: A*, b: B); new Malformed(0)")) {
      case Error("Malformed", "Constructor has malformed definition") :: Nil =>
    }
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

    val seq = file.depthFirst.findByType(classOf[ScClass])
    Compatibility.seqClass = seq

    try {
      file.depthFirst.filterByType(classOf[ScConstructor]).foreach {
        annotator.annotateConstructor(_, mock)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}