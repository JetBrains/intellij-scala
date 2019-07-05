package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScSelfInvocationAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation

class SelfInvocationAnnotatorTest extends SimpleTestCase {

  def messagesForNonGeneric(auxConstr: String): List[Message] = messages(
    s"""
      |class NonGenericTest(a: Int, b: Int) {
      |  def this() = $auxConstr
      |}
    """.stripMargin
  )

  def messagesForNonGenericMulti(auxConstr: String): List[Message] = messages(
    s"""
       |class NonGenericTest(a: Int, b: Int)(c: Int) {
       |  def this(d: Boolean, e: Boolean) = this(1, 2)(3)
       |  def this() = $auxConstr
       |}
    """.stripMargin
  )

  def messagesForGeneric(auxConstr: String): List[Message] = messages(
    s"""
      |class GenericTest[T](a: Int)(b: T) {
      |  def this(c: Boolean, d: Boolean) = this(1)(???)
      |  def this(t: T) = $auxConstr
      |}
    """.stripMargin
  )

  def testFineNonGeneric(): Unit = {
    assertNothing(messagesForNonGeneric("this(1, 2)"))
    assertNothing(messagesForNonGenericMulti("this(1, 2)(3)"))
    assertNothing(messagesForNonGenericMulti("this(true, true)"))
    assertNothing(messagesForGeneric("this(1)(t)"))
    assertNothing(messagesForGeneric("this(true, true)"))
  }

  def testExcessArguments() {
    assertMatches(messagesForNonGeneric("this(1, 2, 3)")) {
      case Error(", 3", "Too many arguments for constructor(Int, Int)") :: Nil =>
    }

    assertMessagesSorted(messagesForNonGenericMulti("this(1, 2, false)"))(
      Error(", f", "Too many arguments for constructor(Int, Int)(Int)"),
      Error(", f", "Too many arguments for constructor(Boolean, Boolean)")
    )

    assertMessagesSorted(messagesForNonGenericMulti("this(0, 1)(2, 3)"))(
      Error(", 3", "Too many arguments for constructor(Int, Int)(Int)")
    )
  }

  def testAutoTupling(): Unit = {
    val code =
      """
        |class Test(a: ())(b: ()) {
        |  def this() = this()()
        |}
      """.stripMargin

    assertNothing(messages(code))
  }

  def testMissedParameters(): Unit = {
    assertMessages(messagesForNonGeneric("this(0)"))(
      Error("0)", "Unspecified value parameters: b: Int")
    )
    assertMessages(messagesForNonGeneric("this()"))(
      Error("()", "Unspecified value parameters: a: Int, b: Int")
    )

    assertMessagesSorted(messagesForNonGenericMulti("this()"))(
      Error("()", "Unspecified value parameters: a: Int, b: Int"),
      Error("()", "Unspecified value parameters: d: Boolean, e: Boolean")
    )

    assertMessagesSorted(messagesForNonGenericMulti("this(0, 1)()"))(
      Error("()", "Unspecified value parameters: c: Int")
    )
  }


  def testMissingArgumentClause(): Unit = {
    assertMessagesSorted(messagesForNonGenericMulti("this(1, 2)"))(
      Error(")", "Missing argument list for constructor(Int, Int)(Int)")
    )
  }

  def testPositionalAfterNamed(): Unit = {
    assertMessagesSorted(messagesForNonGeneric("this(b = 1, 0)")) (
      Error("0", "Positional after named argument")
    )
  }

  def testNamedDuplicates() {
    assertMessagesSorted(messagesForNonGeneric("this(a = 0, a = 1)"))(
      Error("a", "Parameter specified multiple times"),
      Error("a", "Parameter specified multiple times")
    )
  }



  def testTypeMismatch() {
    assertMessagesSorted(messagesForNonGeneric("this(0, false)"))(
      Error("false", "Type mismatch, expected: Int, actual: Boolean")
    )

    assertMessagesSorted(messagesForNonGeneric("this(true, false)"))(
      Error("true", "Type mismatch, expected: Int, actual: Boolean") // SCL-15592
    )

    assertMessagesSorted(messagesForNonGenericMulti("this(true, 1)"))(
      Error("true", "Type mismatch, expected: Int, actual: Boolean"),
      Error("1", "Type mismatch, expected: Boolean, actual: Int")
    )

    assertMessagesSorted(messagesForNonGenericMulti("this(0, 1)(true)"))(
      Error("true", "Type mismatch, expected: Int, actual: Boolean")
    )

    // Generic
    assertMessagesSorted(messagesForGeneric("this(1)(false)"))(
      Error("false", "Type mismatch, expected: T, actual: Boolean")
    )
  }

  def testMissingAndTypeMismatch() {
    assertMessagesSorted(messagesForGeneric("this(true)"))(
      Error("e)", "Unspecified value parameters: d: Boolean"),
      Error("true", "Type mismatch, expected: Int, actual: Boolean")
    )
  }

  def testMalformedSignature() {
    val code =
      """
        |class A
        |class B
        |class Malformed(a: A*, b: B) {
        |  def this() = this(0)
        |}
      """.stripMargin
    assertMessages(messages(code))(
      Error("this", "Constructor has malformed definition")
    )
  }

  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val file: ScalaFile = code.parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().instancesOf[ScSelfInvocation].foreach { constr =>
      ScSelfInvocationAnnotator.annotate(constr)
    }

    mock.annotations
  }
}
