package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.junit.Assert.assertEquals

/**
 * Nikolay.Tropin
 * 2014-04-03
 */
class PatternAnnotatorTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  private def fruitless(exprType: String, patType: String) = ScalaBundle.message("fruitless.type.test", exprType, patType)
  private def incompatible(exprType: String, patType: String) = ScalaBundle.message("scrutinee.incompatible.pattern.type", exprType, patType)
  private def cannotBeUsed(typeText: String) = s"type $typeText cannot be used in a type pattern or isInstanceOf test"
  private def patternTypeIncompatible(found: String, required: String) =
    ScalaBundle.message("pattern.type.incompatible.with.expected", found, required)
  private def constructorCannotBeInstantiatedExectedType(found: String, required: String) =
    ScalaBundle.message("constructor.cannot.be.instantiated.expected.type", found, required)

  private def collectAnnotatorMessages(text: String): List[Message] = {
    configureFromFileTextAdapter("dummy.scala", text)
    val mock = new AnnotatorHolderMock
    val annotator = new PatternAnnotator {}
    val patterns = getFileAdapter.depthFirst.collect {
      case p: ScPattern => p
    }
    patterns.foreach(p => annotator.annotatePattern(p, mock, highlightErrors = true))
    mock.annotations
  }

  private def emptyMessages(text: String): Unit = {
    assertEquals(Nil, collectAnnotatorMessages(text))
  }

  private def collectWarnings(text: String): List[Message] = collectAnnotatorMessages(text).filter {
    case _: Warning => true
    case _ => false
  }

  private def checkWarning(text: String, element: String, expectedMsg: String): Unit = {
    collectWarnings(text) match {
      case Warning(`element`, `expectedMsg`) :: Nil =>
      case actual => assert(assertion = false, s"expected: ${Warning(element, expectedMsg)}, actual: $actual")
    }
  }

  private def collectErrors(text: String): List[Message] = collectAnnotatorMessages(text).filter {
    case error: Error => true
    case _ => false
  }

  private def checkError(text: String, element: String, expectedMsg: String): Unit = {
    collectErrors(text) match {
      case Error(`element`, `expectedMsg`) :: Nil =>
      case actual => assert(assertion = false, s"expected: ${Error(element, expectedMsg)}, actual: $actual")
    }
  }

  private def assertNoErrors(text: String): Unit = {
    assert(collectErrors(text).isEmpty)
  }

  private def assertNoWarnings(text: String): Unit = {
    assert(collectWarnings(text).isEmpty)
  }

  def testSomeConstructor(): Unit = {
    val code: String = "val Some(x) = None"
    checkError(code, "Some(x)", constructorCannotBeInstantiatedExectedType("Some[A]", "None.type"))
    assertNoWarnings(code)
  }

  def testVectorNil(): Unit = {
    val code: String = "val Vector(a) = Nil"
    checkError(code, "Vector(a)", patternTypeIncompatible("Vector[A]", "Nil.type"))
    assertNoWarnings(code)
  }

  def testListToPattern(): Unit = {
    val code: String = "val Vector(a) = List(1)"
    checkError(code, "Vector(a)", patternTypeIncompatible("Vector[A]", "List[Int]"))
    assertNoWarnings(code)
  }

  def testSeqToListNoMessages(): Unit = {
    emptyMessages("val Seq(a) = List(1)")
  }

  def testVectorToSeqEmptyMessages(): Unit = {
    emptyMessages("val Vector(a) = Seq(1)")
  }

  def testConstructorPatternFruitless(): Unit = {
    val code: String = "val List(seq: Seq[Int]) = List(List(\"\"))"
    checkWarning(code, "seq: Seq[Int]", fruitless("List[String]", "Seq[Int]") + ScalaBundle.message("erasure.warning"))
    assertNoErrors(code)
  }

  def testStableIdPattern() {
    //checkWarning("val xs = List(\"\"); val a :: `xs` = 1 :: List(1)", "`xs`", fruitless("List[Int]", "List[String]"))
    emptyMessages(
      """
        |val xs = List("")
        |val a :: `xs` = 1 :: List(1)
      """.stripMargin)
  }

  def testLiteralPattern(): Unit = {
    val code: String = "val \"a\" :: xs = 1 :: Nil"
    checkError(code, "\"a\"", patternTypeIncompatible("String", "Int"))
    assertNoWarnings(code)
  }

  def testNullLiteralPattern(): Unit = {
    val code: String = "val null :: xs = 1 :: Nil"
    checkError(code, "null", patternTypeIncompatible("Null", "Int"))
    assertNoWarnings(code)
  }

  def testNullLiteralNoError(): Unit = {
    emptyMessages("val null :: xs = \"1\" :: Nil")
  }

  def testTuple2ToTuple3Constructor(): Unit = {
    val code: String = "val (x, y) = (1, 2, 3)"
    checkError(code, "(x, y)", constructorCannotBeInstantiatedExectedType("(T1, T2)", "(Int, Int, Int)"))
    assertNoWarnings(code)
  }

  def testTupleWrongDeclaredType(): Unit = {
    val code: String = "val (x: String, y) = (1, 2)"
    checkError(code, "x: String", incompatible("String", "Int"))
    assertNoWarnings(code)
  }

  def testTuplePatternAnyRef(): Unit = {
    emptyMessages("def a: AnyRef = null; val (x, y) = a")
  }

  def testIncompatibleSomeConstructor(): Unit = {
    val code: String = "val Some(x: Int) = \"\""
    checkError(code, "Some(x: Int)", constructorCannotBeInstantiatedExectedType("Some[A]", "String"))
    assertNoWarnings(code)
  }

  def testIncompatibleCons(): Unit = {
    val code: String = "val (x: Int) :: xs = List(\"1\", \"2\")"
    checkError(code, "x: Int", incompatible("Int", "String"))
    assertNoWarnings(code)
  }

  def testIncompatibleExtractorMatchStmtNonFinalType() = {
    val code =
      """
        |class B
        |case class Foo(i: B)
        |def foo(f: Foo) = f match {
        |  case Foo(s: String) =>
        |}
      """.stripMargin
    checkError(code, "s: String", patternTypeIncompatible("String", "B"))
    assertNoWarnings(code)
  }

  def testNonFinalClass() = {
    //the reason this compiles without errors is that equals in A can be overriden.
    //for more see http://stackoverflow.com/questions/33354987/stable-identifier-conformance-check/
    emptyMessages(
      """
        |object Test {
        |  def foo (b: Bar, a: A) = {
        |    b match {
        |      case `a` => println("A")
        |      case _ => println(":(")
        |    }
        |  }
        |
        |  class A
        |  case class Bar(s: String)
        |}
      """.stripMargin)
  }

  def testErrorFinalClass(): Unit = {
    val code =
      """
        |object Test {
        |  def foo (b: Bar, a: A) = {
        |    b match {
        |      case `a` => println("A")
        |      case _ => println(":(")
        |    }
        |  }
        |
        |  final class A
        |  case class Bar(s: String)
        |}
      """.stripMargin
    checkError(code, "`a`", patternTypeIncompatible("A", "Bar"))
    assertNoWarnings(code)
  }

  def testLiteral() = {
    val text = """
      |object Foo {
      |  def foo(i: String) = {
      |    i match {
      |      case 2 =>
      |    }
      |  }
      |}
    """.stripMargin
    checkError(text, "2", patternTypeIncompatible("Int", "String"))
    assertNoWarnings(text)
  }


  def testCannotBeUsed() {
    val anyValCode =
      """
        |1 match {
        |  case _: AnyVal =>
        |}
      """.stripMargin.replace("\r", "")
    val nullCode =
      """
        |2 match {
        |  case n: Null =>
        |}
      """.stripMargin.replace("\r", "")
    val nothingCode =
      """
        |3 match {
        |  case n: Nothing =>
        |}
      """.stripMargin.replace("\r", "")
    checkError(anyValCode,  "_: AnyVal", cannotBeUsed("AnyVal"))
    assertNoWarnings(anyValCode)
    checkError(nullCode, "n: Null", cannotBeUsed("Null"))
    assertNoWarnings(nullCode)
    checkError(nothingCode, "n: Nothing", cannotBeUsed("Nothing"))
    assertNoWarnings(nothingCode)
  }

  def testUncheckedRefinement() {
    checkWarning("val Some(x: AnyRef{def foo(i: Int): Int}) = Some(new AnyRef())", "AnyRef{def foo(i: Int): Int}",
      ScalaBundle.message("pattern.on.refinement.unchecked"))
  }
}
