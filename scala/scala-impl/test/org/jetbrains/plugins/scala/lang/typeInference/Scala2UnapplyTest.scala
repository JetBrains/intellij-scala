package org.jetbrains.plugins.scala.lang.typeInference

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory.SimpleTestData
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala2UnapplyTest extends TestCase

object Scala2UnapplyTest extends GeneratedTestSuiteFactory.withHighlightingTest(ScalaVersion.Latest.Scala_2_13) {
  // See https://www.scala-lang.org/files/archive/spec/2.13/08-pattern-matching.html#constructor-patterns
  lazy val testData: Seq[SimpleTestData] = Seq(
    // ============= Boolean Match =============
    """
      |// booleanExtractor
      |object A {
      |  def unapply(i: Int): Boolean = true
      |}
      |object B {
      |  def unapply(i: Int): true = true
      |}
      |
      |val A() = 1
      |val B() = 2
      |""".stripMargin,
    """
      |// booleanExtractorForTypeParam
      |class A[T](a: T) {
      |  def unapply(i: Int): T = a
      |}
      |
      |object a extends A(true)
      |
      |val a() = 1
      |""".stripMargin,
//    Let's ignore this test case.
//    The compiler, as of now, fails here, but it seems to be a bug in the compiler.
//    It works in Scala 3 and afaict it should work in Scala 2 according to the specs as well.
//    But it is such an edge case so let's not bother.
//    """
//      |// booleanExtractorForUnapplyWithTypeParam
//      |object A {
//      |  def unapply[T](t: T): T = t
//      |}
//      |
//      |val A() = true   // Error
//      |""".stripMargin,
    """
      |// booleanExtractorForUnapplyWithTypeParam2
      |class AProto[T] {
      |  def unapply(t: T): T = t
      |}
      |
      |val A = new AProto[Boolean]
      |
      |val A() = true
      |""".stripMargin,
    """
      |// booleanExtractorForUnapplyWithBooleanTypeParam
      |object A {
      |  def unapply[T <: Boolean](t: T): T = t
      |}
      |
      |val A() = true
      |""".stripMargin,
    // ============= Constructor Patterns =============
    """
      |// testConstructorPatternWithTuple
      |case class Test(param: (Int, Boolean))
      |
      |val Test(t) = Test(1, true)
      |val _t: (Int, Boolean) = t
      |
      |val Test((i, b)) = Test(1, true)
      |val _i: Int = i
      |val _b: Boolean = b
      |""".stripMargin,
    """
      |// testConstructorPatternNoUntupling
      |case class Test(param: (Int, Boolean))
      |
      |val Test(i, b) = Test(1, true)   // Error
      |""".stripMargin,
    """
      |// testNoConstructorPatternWithCustomUnapply
      |case class Test(param: (Int, Boolean))
      |
      |object Test {
      |  def unapply(t: Test): Some[(Int, Boolean)] = ???
      |}
      |
      |val Test(t) = Test(1, true)
      |val _t: (Int, Boolean) = t
      |
      |val Test(i, b) = Test(1, true)
      |val _i: Int = i
      |val _b: Boolean = b
      |""".stripMargin,
    // ============= Extractor Pattern =============
    """
      |// extractorPattern
      |class Result {
      |  val get: String = ???
      |  def isEmpty: Boolean = false
      |}
      |object A {
      |  def unapply(i: Int): Result = new Result
      |}
      |
      |val A(s) = 1
      |val _s: String = s
      |""".stripMargin,
    """
      |// extractorPatternWithTuple
      |class Result {
      |  val get: (Boolean, String) = ???
      |  def isEmpty: Boolean = false
      |}
      |object A {
      |  def unapply(i: Int): Result = new Result
      |}
      |
      |val A(t) = 1
      |val _t: (Boolean, String) = t
      |
      |val A(b, s) = 1
      |val _b: Boolean = b
      |val _s: String = s
      |""".stripMargin,
    """
      |//extractorPatternWithTypeParam
      |
      |class Result[T](val get: T) {
      |  def isEmpty: Boolean = false
      |}
      |
      |object A {
      |  def unapply[T](t: T): Result[T] = new Result(t)
      |}
      |
      |{
      |  val A(s) = "s"
      |  val _s: String = s
      |}
      |{
      |  val A(t) = (1, true)
      |  val _t: (Int, Boolean) = t
      |}
      |{
      |  val A(i, b) = (1, true)
      |  val _i: Int = i
      |  val _b: Boolean = b
      |}
      |""".stripMargin,
    """
      |//extractorPatternWithAccessors
      |
      |class Custom(val _1: Int, val _2: String)
      |
      |class Result {
      |  val get: Custom = ???
      |  def isEmpty: Boolean = false
      |}
      |
      |object A {
      |  def unapply(i: Int): Result = new Result
      |}
      |
      |{
      |  val A(c) = 1
      |  val _c: Custom = c
      |}
      |
      |{
      |  val A(i, s) = 1
      |  val _i: Int = i
      |  val _s: String = s
      |}
      |""".stripMargin,
    """
      |//extractorPatternWithAccessorsError
      |
      |class Custom(val _1: Int, val _2: String)
      |
      |class Result {
      |  val get: Custom = ???
      |  def isEmpty: Boolean = false
      |}
      |
      |object A {
      |  def unapply(i: Int): Result = new Result
      |}
      |
      |val A(i, s, err) = 1   // Error
      |""".stripMargin,
    """
      |//extractorPatternNoDirectAccessors
      |
      |class Custom(val _1: Int, val _2: String)
      |
      |object A {
      |  def unapply(i: Int): Custom = ???
      |}
      |
      |val A(err1, err2) = 1   // Error
      |""".stripMargin,
    """
      |//extractorPatternNoOneAccessors
      |
      |class Custom(val _1: Int)
      |
      |class Result {
      |  val get: Custom = ???
      |  def isEmpty: Boolean = false
      |}
      |
      |object A {
      |  def unapply(i: Int): Result = ???
      |}
      |
      |val A(c) = 1
      |val _c: Custom = c
      |""".stripMargin,
    """
      |//caseClassSimple
      |case class A(i: Int)
      |
      |val A(i) = A(1)
      |val _i: Int = i
      |""".stripMargin,
    """
      |//caseClassTwoArgs
      |case class A(i: Int, s: String)
      |
      |val A(i, s) = A(???, ???)
      |val _i: Int = i
      |val _s: String = s
      |""".stripMargin,
    """
      |//caseClassNoUntupling
      |case class A(i: (Int, String))
      |
      |val A(tup) = A(???)
      |val _tup: (Int, String) = tup
      |
      |val A(wrong1, wrong2) = A(???)    // Error
      |""".stripMargin,
    """
      |// caseClassNoTupleCrushing
      |case class A(i: Int, s: String)
      |
      |val A(tup) = A(???, ???)    // Error
      |val _tup: (Int, String) = tup
      |""".stripMargin,
    """
      |// caseClassTupleCrushingWithCustomUnapply
      |case class A(i: Int, s: String)
      |object A {
      |  def unapply(t: A): Some[(Int, String)] = Some((t.i, t.s))
      |}
      |
      |// Crushing is allowed here
      |val A(tup) = A(???, ???)
      |val _tup: (Int, String) = tup
      |
      |val A(i, s) = A(???, ???)
      |val _i: Int = i
      |val _s: String = s
      |""".stripMargin,
    """
      |//caseClassNoTupleMembers
      |case class A(i: Int, s: String)
      |
      |val a = A(1, "")
      |
      |val x = a._1 // Error
      |""".stripMargin
  ).map(testDataFromCode)
}
