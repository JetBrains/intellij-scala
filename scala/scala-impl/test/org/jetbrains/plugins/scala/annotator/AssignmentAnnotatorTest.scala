package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScAssignmentAnnotator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment
import org.junit.Assert

class AssignmentAnnotatorTest extends AnnotatorSimpleTestCase {
  import Message._

  final val Header = """
  class A; class B
  object A extends A; object B extends B
  """

  def testVariable(): Unit = {
    assertMatches(messages("var v = A; v = A")) {
      case Nil =>
    }
  }

  //todo: requires Function1 trait in scope
  /*def testImplicitConversion {
    assertMatches(messages("implicit def toA(b: B) = A; var v = A; v = B")) {
      case Nil =>
    }
  }*/

  def testValue(): Unit = {
    assertMatches(messages("val v = A; v = A")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("val v = A; v = B")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }

  def testFunctionParameter(): Unit = {
    assertMatches(messages("def f(p: A) { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("def f(p: A) { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }

  def testClassParameter(): Unit = {
    assertMatches(messages("case class C(var p: A) { p = A }")) {
      case Nil =>
    }
    assertMatches(messages("class C(p: A) { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }

  def testClassVariableParameter(): Unit = {
    assertMatches(messages("class C(var p: A) { p = A }")) {
      case Nil =>
    }
  }

  def testClassValueParameter(): Unit = {
    assertMatches(messages("class C(val p: A) { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("class C(val p: A) { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }

  def testFunctionLiteralParameter(): Unit = {
    assertMatches(messages("(p: A) => { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("(p: A) => { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }

  //TODO fails on server
//  def testParameterInsideBlock {
//    assertMatches(messages("{ p: A => p = A }")) {
//      case Error("p = A", ReassignmentToVal()) :: Nil =>
//    }
//    assertMatches(messages("{ p: A => p = B }")) {
//      case Error("p = B", ReassignmentToVal()) :: Nil =>
//    }
//  }

  def testForComprehensionGenerator(): Unit = {
    assertMatches(messages("for(v: A <- null) { v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("for(v: A <- null) { v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }

  def testForComprehensionBinding(): Unit = {
    assertMatches(messages("for(x <- null; v = A) { v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("for(x <- null; v = A) { v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }

  def testCaseClause(): Unit = {
    assertMatches(messages("A match { case v: A => v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("A match { case v: A => v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }

  def testNamedParameterClause(): Unit = {
    assertMatches(messages("def blerg(a: Any)= 0; blerg(a = 0)")) {
      case Nil =>
    }
  }

  def testUpdateOkay(): Unit = {
    assertMatches(messages("val a = new { def update(x: Int): Unit = () }; a() = 1")) {
      case Nil =>
    }
  }

  def testVarInsideVar(): Unit = {
    assertMatches(messages("val x = { var a = A; a = A }")) {
      case Nil =>
    }
  }

  def testVarInsideTemplateAssignedToVal(): Unit = {
    assertMatches(messages("val outer = new { var a = (); a = () }")) {
      case Nil =>
    }
  }

  def testSetter(): Unit = {
    assertMatches(messages("def a = A; def a_=(x: A) {}; a = A")) {
      case Nil =>
    }
    assertMatches(messages("def a(implicit b: B) = A; def a_=(x: A) {}; a = A")) {
      case Nil =>
    }
    assertMatches(messages("def a() = A; def a_=(x: A) {}; a = A")) {
      case Nil =>
    }
    assertMatches(messages("val a = A; def a_=(x: A) {}; a = A")) {
      case Error("a = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("def `a` = A; def a_=(x: A) {}; a = A")) {
      case Nil =>
    }
  }

  def testUnarySetter(): Unit = {
    val code =
      """
        |class C {
        |  def unary_! : C = this
        |  def `unary_!_=`(value: A): Unit = ()
        |}
        |val c = new C
        |!c = A
        |""".stripMargin

    assertMatches(messages(code)) {
      case Nil =>
    }

    Assert.assertEquals("`unary_!_=`", assignment(code).resolveAssignment.map(_.element.name).orNull)
  }

  def testUnaryGetterWithoutSetter(): Unit = {
    assertMatches(messages(
      """
        |class C {
        |  def unary_! : C = this
        |}
        |val c = new C
        |!c = A
        |""".stripMargin
    )) {
      case Error("!c = A", ReassignmentToVal()) :: Nil =>
    }
  }

  // SCL-22974
  def testUnarySettersWithImplicitClause(): Unit = {
    val code =
      """
        |class MyClass {
        |  def regular: MyClass = this
        |  def unary_! : MyClass = this
        |  def unary_~ : MyClass = this
        |
        |  def `regular_=`[T](value: T)(implicit x: T) : Unit = println(s"assigning foo $value")
        |  def `unary_!_=`[T](value: T)(implicit x: T) : Unit = println(s"assigning ! $value")
        |  def `unary_~_=`[T](value: T)(implicit x: T) : Unit = println(s"assigning ~ $value")
        |
        |  def main(args: Array[String]): Unit = {
        |    val a = new MyClass
        |    a.regular
        |    !a
        |    ~a
        |
        |    implicit val s: String = "23"
        |
        |    a.regular = "42"
        |    !a = "42"
        |    ~a = "42"
        |  }
        |}
        |""".stripMargin

    assertMatches(messagesForAllAssignments(code)) {
      case Nil =>
    }

    Assert.assertEquals(
      Seq("`regular_=`", "`unary_!_=`", "`unary_~_=`"),
      assignments(code).map(_.resolveAssignment.map(_.element.name).orNull)
    )
  }

  // SCL-17962
  def testIllegalAssignments(): Unit = {
    def assertIllegalAssignment(code: String): Unit =
      assertMatches(messages("class C; val a,b = 0; def f() = ();" + code)) {
        case Error("=", IllegalAssignmentTarget()) :: Nil =>
      }

    assertIllegalAssignment("5 = 4")
    assertIllegalAssignment("{ } = 8")
    assertIllegalAssignment("() = 4")
    assertIllegalAssignment("(5) = 4")
    assertIllegalAssignment("(a, b) = (1, 2)")
    assertIllegalAssignment("new C = 3")
    assertIllegalAssignment("1 + 2 = 3")
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val assignment = this.assignment(code)
    val file = assignment.getContainingFile

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    ScAssignmentAnnotator.annotate(assignment, typeAware = true)
    mock.annotations
  }

  private def messagesForAllAssignments(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val allAssignments = assignments(code)
    val file = allAssignments.head.getContainingFile

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    allAssignments.foreach(ScAssignmentAnnotator.annotate(_, typeAware = true))
    mock.annotations
  }

  private def assignment(@Language(value = "Scala", prefix = Header) code: String): ScAssignment =
    assignments(code).head

  private def assignments(@Language(value = "Scala", prefix = Header) code: String): Seq[ScAssignment] =
    (Header + code).parse().depthFirst().filterByType[ScAssignment].toSeq

  val ReassignmentToVal = StartWith("Reassignment to val")
  val IllegalAssignmentTarget = StartWith(ScalaBundle.message("illegal.assignment.target"))

  case class StartWith(fragment: String) {
    def unapply(s: String): Boolean = s.startsWith(fragment)
  }
}
