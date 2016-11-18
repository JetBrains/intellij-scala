package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.junit.Assert
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
  private def constructorCannotBeInstantiated(found: String, required: String) =
    ScalaBundle.message("constructor.cannot.be.instantiated.to.expected.type", found, required)

  private def collectAnnotatorMessages(text: String): List[Message] = {
    configureFromFileTextAdapter("dummy.scala", text)
    val mock = new AnnotatorHolderMock(getFileAdapter)
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
      case actual => Assert.assertTrue(s"expected: ${Warning(element, expectedMsg)}\n actual: $actual", false)
    }
  }

  private def collectErrors(text: String): List[Message] = collectAnnotatorMessages(text).filter {
    case error: Error => true
    case _ => false
  }

  private def checkError(text: String, element: String, expectedMsg: String): Unit = {
    checkErrors(text, List(Error(element, expectedMsg)))
  }

  private def checkErrors(text: String, errors: List[Error]): Unit = {
    Assert.assertEquals(errors, collectErrors(text))
  }

  private def assertNoErrors(text: String): Unit = {
    Assert.assertEquals(List[Error](), collectErrors(text))
  }

  private def assertNoWarnings(text: String): Unit = {
    Assert.assertTrue(collectWarnings(text).isEmpty)
  }

  def testSomeConstructor(): Unit = {
    val code: String = "val Some(x) = None"
    checkError(code, "Some(x)", constructorCannotBeInstantiated("Some[A]", "None.type"))
    assertNoWarnings(code)
  }

  def testVectorNil(): Unit = {
    val code: String = "val Vector(a) = Nil"
    checkError(code, "Vector(a)", constructorCannotBeInstantiated("Vector[A]", "Nil.type"))
    assertNoWarnings(code)
  }

  def testListToPattern(): Unit = {
    val code: String = "val Vector(a) = List(1)"
    checkError(code, "Vector(a)", constructorCannotBeInstantiated("Vector[A]", "List[Int]"))
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
    checkError(code, "(x, y)", patternTypeIncompatible("(Int, Int)", "(Int, Int, Int)"))
    assertNoWarnings(code)
  }

  def testTupleWrongDeclaredType(): Unit = {
    val code: String = "val (x: String, y) = (1, 2)"
    checkErrors(code, List(
      Error("(x: String, y)", patternTypeIncompatible("(String, Int)", "(Int, Int)")),
      Error("x: String", incompatible("String", "Int"))
    ))
    assertNoWarnings(code)
  }

  def testTuplePatternAnyRef(): Unit = {
    emptyMessages("def a: AnyRef = null; val (x, y) = a")
  }

  def testIncompatibleSomeConstructor(): Unit = {
    val code: String = "val Some(x: Int) = \"\""
    checkError(code, "Some(x: Int)", constructorCannotBeInstantiated("Some[A]", "String"))
    assertNoWarnings(code)
  }

  def testIncompatibleCons(): Unit = {
    val code: String = "val (x: Int) :: xs = List(\"1\", \"2\")"
    checkErrors(code, List(
      Error("(x: Int)", patternTypeIncompatible("Int", "String")),
      Error("x: Int", incompatible("Int", "String"))
    ))
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
        |}
        |final class A
        |case class Bar(s: String)
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

  def testSCL8970(): Unit = {
    val code =
      """
        |case class TakeSnapShot(version: Int, promise: Boolean, smth: String])
        |
        |def tpp(t: TakeSnapShot): Unit = t match {
        |  case TakeSnapShot(promise, _) =>
        |}
      """.stripMargin
    checkError(code, "TakeSnapShot(promise, _)", ScalaBundle.message("wrong.number.arguments.extractor", "2", "3"))
    assertNoWarnings(code)
  }

  def testAliasesAreExpanded(): Unit = {
    val code =
      """
        |case class Foo(x: Foo.Bar)
        |
        |object Foo {
        |  type Bar = Char
        |
        |  def getFoo: Foo = Foo('?')
        |  val s = "?"
        |  def fa(f: Foo) = getFoo match {
        |    case Foo(Util.i) =>
        |  }
        |}
        |
        |object Util {
        |  final val i: Byte = 31
        |}
      """.stripMargin
    emptyMessages(code)
  }

  def testUncheckedRefinement() {
    checkWarning("val Some(x: AnyRef{def foo(i: Int): Int}) = Some(new AnyRef())", "AnyRef{def foo(i: Int): Int}",
      ScalaBundle.message("pattern.on.refinement.unchecked"))
  }

  def testExpectedTypeIsTupleIfThereIsOneArgumentAndMoreThanOneArgumentIsReturnedByUnapplySCL8115(): Unit = {
    val code =
      """
        |object unapplier { def unapply(x: Int) = Some((x, x)) }
        |val tupleTaker = (_: (Int, Int)) => ()
        |
        |1 match {
        |  case unapplier(tuple) => tupleTaker(tuple)
        |}
      """.stripMargin
    emptyMessages(code)
  }

  def testVarAsStableIdentifierPattern(): Unit = {
    val code =
      """
        |object CaseIdentifierBug {
        |  var ONE = 1   // note var, not val
        |  var two = 2
        |
        |  1 match
        |  {
        |    case ONE => println("1")   // bad, but not flagged
        |    case `two` => println("2") // ditto
        |    case this.two => println("2")  // this one, too
        |    case _ => println("Not 1")
        |  }
        |}
      """.stripMargin
    val errors =
      Error("ONE", ScalaBundle.message("stable.identifier.required", "ONE")) ::
      Error("`two`", ScalaBundle.message("stable.identifier.required", "`two`")) ::
      Error("this.two", ScalaBundle.message("stable.identifier.required", "this.two")) :: Nil
    checkErrors(code, errors)
    assertNoWarnings(code)
  }

  def testVarClassParameterAsStableIdPattern(): Unit = {
    val code =
      """
        |class Baz(var ONE: Int) {
        |  1 match {
        |    case ONE => println("1") // bad, but not flagged
        |    case _ => println("Not 1")
        |  }
        |}
      """.stripMargin
    checkError(code, "ONE", ScalaBundle.message("stable.identifier.required", "ONE"))
    assertNoWarnings(code)
  }

  def testInfixExpressionIncompatible(): Unit = {

    val code =
      """
        |object Bar {
        |  def main(args: Array[String]) {
        |    1 match {
        |      case foo appliedTo2 ("1", "2") =>
        |    }
        |  }
        |  case class appliedTo2(name: String, arg1: String, arg2: String)
        |}
      """.stripMargin
    checkError(code, "foo appliedTo2 (\"1\", \"2\")", patternTypeIncompatible("Bar.appliedTo2", "Int"))
    assertNoWarnings(code)
  }

  def testInfixExpressionTooManyArguments(): Unit = {
    val code =
      """
        |object Bar {
        |  val x: AnyRef = null
        |  x match {
        |    case foo appliedTo2 ("1", "2", "3", "4") =>
        |  }
        |  case class appliedTo2(name: String, arg1: String, arg2: String)
        |}
      """.stripMargin
    checkError(code, "foo appliedTo2 (\"1\", \"2\", \"3\", \"4\")",
      ScalaBundle.message("wrong.number.arguments.extractor", "5", "3"))
    assertNoWarnings(code)
  }

  def testInfixExpressionTooLittleArguments(): Unit = {
    val code =
      """
        |object Bar {
        |  val x: AnyRef = null
        |  x match {
        |    case foo appliedTo2 ("1", "2") =>
        |    case foo appliedTo2 ("1") =>
        |    case foo appliedTo2 () =>
        |  }
        |  case class appliedTo2(name: String, arg1: String, arg2: String)
        |}
      """.stripMargin
    checkErrors(code, List(
      Error("foo appliedTo2 (\"1\")", ScalaBundle.message("wrong.number.arguments.extractor", "2", "3")),
      Error("foo appliedTo2 ()", ScalaBundle.message("wrong.number.arguments.extractor", "2", "3"))
    ))
    assertNoWarnings(code)
  }

  def testInfixExpressionVararsgs(): Unit = {
    val code =
      """
        |object Bar {
        |  object Bar {
        |    val x: AnyRef = null
        |    x match {
        |      case foo appliedTo2 ("1", "2", "3", 4) =>
        |      case foo appliedTo2 ("1", "2", "3") =>
        |      case foo appliedTo2 ("1") =>
        |      case foo appliedTo2 () =>
        |    }
        |  }
        |
        |
        |  case class appliedTo2(name: String, arg1: String*)
        |}
      """.stripMargin
    checkErrors(code, List(Error("4", patternTypeIncompatible("Int", "String"))))
    assertNoWarnings(code)
  }

  def testNumberOfArgumentaUnapplySeq(): Unit = {
    val code =
      """
        |object Bar {
        |  val x: AnyRef = null
        |  x match {
        |    case foo appliedTo "" =>
        |  }
        |  case class appliedTo(name: String, arg1: String, arg2: String, otherArgs: String*)
        |}
      """.stripMargin
    checkError(code, "foo appliedTo \"\"", ScalaBundle.message("wrong.number.arguments.extractor.unapplySeq", "2", "3"))
  }

  def testNumberOfArgumentsCons(): Unit = {
    val code =
      """
        |object Bar {
        |  List(1, 2, 3) match {
        |    case 1 :: 2 :: 3 :: Nil =>
        |  }
        |}
      """.stripMargin
    emptyMessages(code)
  }

  def testTupleCrushingNotPresentWithCaseClasses(): Unit = {
    val code =
      """
        |sealed trait RemoteProcessResult {
        |  def id: Long
        |}
        |
        |case class RemoteProcessSuccess(id: Long, info: String) extends RemoteProcessResult
        |
        |case class RemoteProcessFailed(id: Long, why: String) extends RemoteProcessResult
        |
        |object Foo {
        |  def foo(i: Any) {
        |    i match {
        |      case RemoteProcessFailed(why) =>
        |    }
        |  }
        |}
      """.stripMargin
    assertNoWarnings(code)
    checkError(code, "RemoteProcessFailed(why)", ScalaBundle.message("wrong.number.arguments.extractor", "1", "2"))
  }

  def testNonFinalConstructorPattern(): Unit = {
    val text =
      """
        |object Moo {
        |  (1, 2) match {
        |    case ScFunctionType(_) =>
        |    case _ =>
        |  }
        |
        |  object ScFunctionType {
        |    def unapply(tp: Foo): Option[(Foo, Seq[Foo])] = ???
        |  }
        |}
        |
        |class Foo
      """.stripMargin
    assertNoErrors(text)
    checkWarning(text, "ScFunctionType(_)", fruitless("(Int, Int)", "Foo"))
  }

  def testInfixPatternWithConstructorOnTheRight(): Unit = {
    val text =
      """
        |List(1, 2, 3) match {
        |  case 1 :: List(x, y) =>
        |  case _ =>
        |}
      """.stripMargin
    assertNoErrors(text)
    assertNoWarnings(text)
  }

  def testTupleWildcardWrongNumberOfParams(): Unit = {
    val text =
      """
        |object Foo {
        |  def makeTuple4(): (Int, Int, Int, Int) = (1, 2, 3, 4)
        |
        |  val (one, _): (Int, Int, Int, Int) = makeTuple4()
        |}
      """.stripMargin
    assertNoWarnings(text)
    checkError(text, "(one, _)", patternTypeIncompatible("(Int, Int)", "(Int, Int, Int, Int)"))
  }

  def testSealedClassesInheritors(): Unit = {
    val text =
      """
        |object Koo {
        |  sealed trait A
        |  sealed trait B
        |
        |  case class AwithB(whatever: String) extends A with B
        |  val a: A = AwithB("example")
        |
        |  a match {
        |    case b: B =>
        |      println("A is a B")
        |    case other =>
        |      println("A is not a B")
        |  }
        |}
      """.stripMargin
    assertNoWarnings(text)
    assertNoErrors(text)
  }

  /*def testNonFinalCaseClassConstructorPattern(): Unit = {
    val code =
      """
        |object Moo {
        |  (1, 2) match {
        |    case ScFunctionType(_) =>
        |    case _ =>
        |  }
        |}
        |case class ScFunctionType(a: Foo, b: Seq[Foo])
        |class Foo
      """.stripMargin
    assertNoWarnings(code)
    checkError(code, "ScFunctionType(_)", constructorCannotBeInstantiated("ScFunctionType", "(Int, Int)"))
  }*/

}
