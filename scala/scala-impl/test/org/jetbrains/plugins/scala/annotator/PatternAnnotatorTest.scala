package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.element.ScPatternAnnotator
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.{ScalaBundle, TypecheckerTests}
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class PatternAnnotatorTest extends ScalaLightCodeInsightFixtureTestCase {
  import Message._

  private def fruitless(exprType: String, patType: String) = ScalaBundle.message("fruitless.type.test", exprType, patType)
  private def incompatible(exprType: String, patType: String) = ScalaBundle.message("scrutinee.incompatible.pattern.type", exprType, patType)
  private def cannotBeUsed(typeText: String) = s"type $typeText cannot be used in a type pattern or isInstanceOf test"
  private def patternTypeIncompatible(found: String, required: String) =
    ScalaBundle.message("pattern.type.incompatible.with.expected", found, required)
  private def constructorCannotBeInstantiated(found: String, required: String) =
    ScalaBundle.message("constructor.cannot.be.instantiated.to.expected.type", found, required)

  //////////////////////////////////////////

  private def collectAnnotatorMessages(text: String): List[Message] = {
    configureFromFileText("dummy.scala", text)
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(getFile)
    val patterns = getFile.depthFirst().filterByType[ScPattern]
    patterns.foreach(ScPatternAnnotator.annotate(_, typeAware = true))
    mock.annotations
  }

  private def collectWarnings(text: String): List[Message] = {
    val messages = collectAnnotatorMessages(text)
    messages.filterByType[Warning]
  }

  private def collectErrors(text: String): List[Message] = {
    val messages = collectAnnotatorMessages(text)
    messages.filterByType[Error]
  }

  //////////////////////////////////////////

  private def assertMessages(text: String, expected: List[Message]): Unit = {
    val actual = collectAnnotatorMessages(text)
    assertEquals(expected, actual)
  }

  private def assertWarnings(text: String, expected: List[Warning]): Unit = {
    val actual = collectWarnings(text)
    assertEquals(expected, actual)
  }

  private def assertErrors(text: String, errors: List[Error]): Unit = {
    val actualErrors = collectErrors(text)
    assertEquals(errors, actualErrors)
  }

  private def assertWarning(text: String, element: String, expectedMsg: String): Unit = {
    assertWarnings(text, List(Warning(element, expectedMsg)))
  }

  private def assertError(text: String, element: String, expectedMsg: String): Unit = {
    assertErrors(text, List(Error(element, expectedMsg)))
  }

  private def assertNoMessages(text: String): Unit = {
    assertMessages(text, Nil)
  }

  private def assertNoErrors(text: String): Unit = {
    assertErrors(text, Nil)
  }

  private def assertNoWarnings(text: String): Unit = {
    assertWarnings(text, Nil)
  }

  //////////////////////////////////////////

  def testSomeConstructor(): Unit = {
    val code: String = "val Some(x) = None"
    assertError(code, "Some(x)", constructorCannotBeInstantiated("Some[A]", "None.type"))
    assertNoWarnings(code)
  }

  def testVectorNil(): Unit = {
    val code: String = "val Vector(a) = Nil"
    assertError(code, "Vector(a)", constructorCannotBeInstantiated("Vector[A]", "Nil.type"))
    assertNoWarnings(code)
  }

  def testListToPattern(): Unit = {
    val code: String = "val Vector(a) = List(1)"
    assertError(code, "Vector(a)", constructorCannotBeInstantiated("Vector[A]", "List[Int]"))
    assertNoWarnings(code)
  }

  def testSeqToListNoMessages(): Unit = {
    assertNoMessages("val Seq(a) = List(1)")
  }

  def testVectorToSeqEmptyMessages(): Unit = {
    assertNoMessages("val Vector(a) = Seq(1)")
  }

  def testConstructorPatternFruitless(): Unit = {
    val code: String = "val List(seq: Seq[Int]) = List(List(\"\"))"
    assertWarning(code, "seq: Seq[Int]", fruitless("List[String]", "Seq[Int]") + ScalaBundle.message("erasure.warning"))
    assertNoErrors(code)
  }

  def testStableIdPattern(): Unit = {
    assertNoMessages(
      """
        |val xs = List("")
        |val a :: `xs` = 1 :: List(1)
      """.stripMargin)
  }

  def testLiteralPattern(): Unit = {
    val code: String = "val \"a\" :: xs = 1 :: Nil"
    assertError(code, "\"a\"", patternTypeIncompatible("String", "Int"))
    assertNoWarnings(code)
  }

  def testNullLiteralPattern(): Unit = {
    val code: String = "val null :: xs = 1 :: Nil"
    assertError(code, "null", patternTypeIncompatible("Null", "Int"))
    assertNoWarnings(code)
  }

  def testNullLiteralNoError(): Unit = {
    assertNoMessages("val null :: xs = \"1\" :: Nil")
  }

  def testTuple2ToTuple3Constructor(): Unit = {
    val code: String = "val (x, y) = (1, 2, 3)"
    assertError(code, "(x, y)", patternTypeIncompatible("(Any, Any)", "(Int, Int, Int)"))
    assertNoWarnings(code)
  }

  def testTupleWrongDeclaredType(): Unit = {
    val code: String = "val (x: String, y) = (1, 2)"
    assertErrors(code, List(
      Error("(x: String, y)", patternTypeIncompatible("(String, Any)", "(Int, Int)")),
      Error("x: String", incompatible("String", "Int"))
    ))
    assertNoWarnings(code)
  }

  def testTuplePatternAnyRef(): Unit = {
    assertNoMessages("def a: AnyRef = null; val (x, y) = a")
  }

  def testIncompatibleSomeConstructor(): Unit = {
    val code: String = "val Some(x: Int) = \"\""
    assertError(code, "Some(x: Int)", constructorCannotBeInstantiated("Some[A]", "String"))
    assertNoWarnings(code)
  }

  def testIncompatibleCons(): Unit = {
    val code: String = "val (x: Int) :: xs = List(\"1\", \"2\")"
    assertErrors(code, List(
      Error("(x: Int)", patternTypeIncompatible("Int", "String")),
      Error("x: Int", incompatible("Int", "String"))
    ))
    assertNoWarnings(code)
  }

  def testIncompatibleExtractorMatchStmtNonFinalType(): Unit = {
    val code =
      """
        |class B
        |case class Foo(i: B)
        |def foo(f: Foo) = f match {
        |  case Foo(s: String) =>
        |}
      """.stripMargin
    assertError(code, "s: String", patternTypeIncompatible("String", "B"))
    assertNoWarnings(code)
  }

  def testNonFinalClass(): Unit = {
    //the reason this compiles without errors is that equals in A can be overridden.
    //for more see https://stackoverflow.com/questions/33354987/stable-identifier-conformance-check/
    assertNoMessages(
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
    assertError(code, "`a`", patternTypeIncompatible("A", "Bar"))
    assertNoWarnings(code)
  }

  def testLiteral(): Unit = {
    val text = """
      |object Foo {
      |  def foo(i: String) = {
      |    i match {
      |      case 2 =>
      |    }
      |  }
      |}
    """.stripMargin
    assertError(text, "2", patternTypeIncompatible("Int", "String"))
    assertNoWarnings(text)
  }


  def testCannotBeUsed(): Unit = {
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
    assertError(anyValCode,  "_: AnyVal", cannotBeUsed("AnyVal"))
    assertNoWarnings(anyValCode)
    assertError(nullCode, "n: Null", cannotBeUsed("Null"))
    assertNoWarnings(nullCode)
    assertError(nothingCode, "n: Nothing", cannotBeUsed("Nothing"))
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
    assertError(code, "TakeSnapShot(promise, _)", ScalaBundle.message("wrong.number.arguments.extractor", "2", "3"))
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
    assertNoMessages(code)
  }

  def testUncheckedRefinement(): Unit = {
    assertWarning("val Some(x: AnyRef{def foo(i: Int): Int}) = Some(new AnyRef())", "AnyRef{def foo(i: Int): Int}",
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
    assertNoMessages(code)
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
    assertErrors(code, errors)
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
    assertError(code, "ONE", ScalaBundle.message("stable.identifier.required", "ONE"))
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
    assertError(code, "foo appliedTo2 (\"1\", \"2\")", patternTypeIncompatible("Bar.appliedTo2", "Int"))
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
    assertError(code, "foo appliedTo2 (\"1\", \"2\", \"3\", \"4\")",
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
    assertErrors(code, List(
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
    assertErrors(code, List(Error("4", patternTypeIncompatible("Int", "String"))))
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
    assertError(code, "foo appliedTo \"\"", ScalaBundle.message("wrong.number.arguments.extractor.unapplySeq", "2", "3"))
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
    assertNoMessages(code)
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
    assertError(code, "RemoteProcessFailed(why)", ScalaBundle.message("wrong.number.arguments.extractor", "1", "2"))
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
    assertWarning(text, "ScFunctionType(_)", fruitless("(Int, Int)", "Foo"))
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
    assertError(text, "(one, _)", patternTypeIncompatible("(Any, Any)", "(Int, Int, Int, Int)"))
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

  def testSCL11331(): Unit = {
    val text =
      """
        |class Extractable[T]{
        |  def unapply(arg:Any): Option[T] = None
        |}
        |object extractorObj extends Extractable[(Int,String)]{
        |  override def unapply(arg:Any): Option[(Int,String)] = None
        |}
        |val extractorVal = new Extractable[(Int,String)]
        |null match {
        |  case extractorVal(int,string) =>
        |  case extractorObj(int,string) =>
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

  def testSCL12977(): Unit = {
    val code =
      """
        |class Unapply[T](fn: String => T) {
        |  def unapply(s: String): T =
        |    fn(s)
        |}
        |
        |val FirstCapitalLetter: Unapply[Option[Char]] =
        |  new Unapply(s => s.headOption.filter(_.isUpper))
        |
        |"Test" match {
        |  case FirstCapitalLetter(letter) => println(s"Starts with: $letter")
        |  case x => println("Does not start with a capital letter")
        |}
      """.stripMargin
    assertNoMessages(code)
  }

  def testSealedTrait(): Unit = {
    val text =
      """
        |
        |sealed trait A
        |class C
        |
        |new C match {
        |  case _: A =>
        |}
        |""".stripMargin
    assertNoErrors(text)
    assertWarning(text, "_: A", fruitless("C", "A"))
  }

  // SCL-18802
  def testTupleCovariance(): Unit = {
    val text =
      """
        |object Test extends App {
        |  sealed trait Option[A] // An Optional data type
        |  object Option {
        |    case class None[A]() extends Option[A]
        |    case class Some[A](a: A) extends Option[A]
        |  }
        |  import Option._
        |  def maxOptional(opt1: Option[Int], opt2: Option[Int]): Option[Int] = (opt1, opt2) match {
        |    case (Some(n), Some(m)) => Some(Math.max(n, m))
        |    case (None(), other) => other
        |    case (other, None()) => other
        |  }
        |  println(maxOptional(Some(1), None()))
        |}
        |""".stripMargin

    assertNoMessages(text)
  }

  def testInvariance(): Unit = {
    val text =
      """
        |sealed trait A
        |trait B extends A
        |
        |sealed trait Base[T]
        |class Impl[T] extends Base[T]
        |
        |def test(base: Base[A]): Unit = base match {
        |  case x: Impl[A] =>
        |  case x: Impl[B] =>
        |  case x: Base[A] =>
        |  case x: Base[B] =>
        |}
        |""".stripMargin

    assertMessages(
      text,
      List(
        Warning("x: Impl[B]", "fruitless type test: a value of type Base[A] cannot also be a Impl[B](but still might match its erasure)"),
        Warning("x: Base[B]", "fruitless type test: a value of type Base[A] cannot also be a Base[B](but still might match its erasure)")
      )
    )
  }

  def testCovariance(): Unit = {
    val text =
      """
        |sealed trait A
        |final class F
        |
        |sealed trait Base[+T]
        |class Impl[T] extends Base[T]
        |
        |def test(base: Base[A]): Unit = base match {
        |  case x: Base[F] =>
        |  case x: Impl[F] =>
        |}
        |""".stripMargin

    assertMessages(
      text,
      List(
        Warning("x: Base[F]", "fruitless type test: a value of type Base[A] cannot also be a Base[F](but still might match its erasure)"),
        Warning("x: Impl[F]", "fruitless type test: a value of type Base[A] cannot also be a Impl[F](but still might match its erasure)")
      )
    )
  }

  def testGenericPattern(): Unit = assertNoErrors(
    """
      |sealed trait A[T]
      |final class B[T] extends A[T]
      |
      |def f3(a: A[Int]) = a match { case b: B[t] => 3 }
      |""".stripMargin
  )

  def testStableIdentifierRequiredInPattern(): Unit = {
    val text =
      """class A {
        |  val ARROW1: String = ???
        |  var ARROW2: String = ???
        |
        |  (null: AnyRef) match {
        |    case code@(ARROW1) =>
        |    case code@(ARROW2) =>
        |  }
        |}
        |""".stripMargin
    assertMessages(
      text,
      List(
        Error("ARROW2", "Stable identifier required but ARROW2 found")
      )
    )
  }

  def testNestedByNameExtraction(): Unit = {
    val text =
      """class Inner() {
        |  def isEmpty: Boolean = false
        |  def get: Inner = this
        |  def _1: Int = 42
        |  def _2: Double = 3.14
        |}
        |
        |object Inner {
        |  def unapply(r: Inner): Inner = r
        |}
        |
        |object Outer {
        |  def unapply(a: Any): Option[Inner] = ???
        |}
        |
        |object Example {
        |  new Inner() match {
        |    case Inner(anInt, aDouble) =>
        |    case Outer(Inner(anInt, aDouble)) =>
        |  }
        |}
        |""".stripMargin

    assertNoWarnings(text)
  }

  def testInfixTypeInPattern(): Unit = assertMessages(
    """
      |type <>[A, B] = String
      |
      |"test" match {
      |  case _: Int <> Byte => 3
      |}
      |
      |val y@(_: Int <> Byte) = "test"
      |""".stripMargin,
    List(
      Error("<>", "Cannot have infix type directly in typed pattern. Try to surround it with parenthesis."),
      Error("<>", "Cannot have infix type directly in typed pattern. Try to surround it with parenthesis."),
    )
  )


  def testInfixTypeInParenInPattern(): Unit = assertNoWarnings(
    """
      |type <>[A, B] = String
      |
      |"test" match {
      |  case _: (Int <> Byte) => 3
      |}
      |val y@_: Int <> Byte = "test"
      |val _: Int <> Byte = "test"
      |""".stripMargin
  )
}
