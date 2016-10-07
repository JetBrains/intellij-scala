package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel.Fatin, 18.05.2010
 */

class FunctionAnnotatorTest extends SimpleTestCase {
  final val Header = "class A; class B\n"

  def testUnitEmpty() {
    assertMatches(messages("def f { }")) {
      case Nil =>
    }
  }

  def testUnitExpression() {
    assertMatches(messages("def f { new A }")) {
      case Nil =>
    }
  }

  def testUnitExpressionUnit() {
    assertMatches(messages("def f { () }")) {
      case Nil =>
    }
  }

  def testUnitReturn() {
    assertMatches(messages("def f { return }")) {
      case Nil =>
    }
  }

  def testUnitReturnType() {
    assertMatches(messages("def f { return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testUnitReturnUnit() {
    assertMatches(messages("def f { return () }")) {
      case Warning("()", RedundantReturnData()) :: Nil =>
    }
  }

  def testAssignNull() {
    assertMatches(messages("def f = null")) {
      case Nil =>
    }
  }

  def testAssignEmpty() {
    assertMatches(messages("def f = { }")) {
      case Nil =>
    }
  }

  def testAssignExpression() {
    assertMatches(messages("def f = { new A }")) {
      case Nil =>
    }
  }

  def testAssignReturn() {
    assertMatches(messages("def f = { return }")) {
      case Error("return", NeedsResultType()) :: Nil =>
    }
  }

  def testAssignReturnExpression() {
    assertMatches(messages("def f = { return new A }")) {
      case Error("return", NeedsResultType()) :: Nil =>
    }
  }

  def testTypeNull() {
    assertMatches(messages("def f: A = null")) {
      case Nil =>
    }
  }

  def testAnyValNull() {
    assertMatches(messages("def f: AnyVal = null")) {
      case Error("null", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeEmpty() {
    assertMatches(messages("def f: A = { }")) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testTry(): Unit = {
    assertMatches(messages(
      """
        |def myFunc(): Int = {
        |  try {
        |    val something = "some string"
        |    val someOtherValue = List()
        |  } catch {
        |    case e: Exception => throw e
        |  }
        |}
      """.stripMargin
    )) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeAbsolutelyEmpty() {
    assertMatches(messages("def f: A = {}")) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeExpression() {
    assertMatches(messages("def f: A = { new A }")) {
      case Nil =>
    }
  }

  def testTypeWrongExpression() {
    assertMatches(messages("def f: A = { new B }")) {
      case Error("new B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeWrongExpressionUnit() {
    assertMatches(messages("def f: A = { () }")) {
      case Error("()", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeWrongExpressionMultiple() {
    assertMatches(messages("def f: A = { if(1 > 2) new B else new B }")) {
      case Error("new B", TypeMismatch()) :: Error("new B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturn() {
    assertMatches(messages("def f: A = { return }")) {
      case Error("return", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeUnitEmpty() {
    assertMatches(messages("def f: Unit = { }")) {
      case Nil =>
    }
  }

  def testTypeUnitExpression() {
    assertMatches(messages("def f: Unit = { new A }")) {
      case Nil =>
    }
  }

  def testTypeUnitExpressionUnit() {
    assertMatches(messages("def f: Unit = { () }")) {
      case Nil =>
    }
  }

  def testTypeUnitReturn() {
    assertMatches(messages("def f: Unit = { return }")) {
      case Nil =>
    }
  }

  def testTypeUnitReturnType() {
    assertMatches(messages("def f: Unit = { return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testTypeUnitReturnUnit() {
    assertMatches(messages("def f: Unit = { return () }")) {
      case Warning("()", RedundantReturnData()) :: Nil =>
    }
  }

  def testTypeReturnType() {
    assertMatches(messages("def f: A = { return new A }")) {
      case Nil =>
    }
  }

  def testInheritedTypeReturnType() {
    assertMatches(messages("trait T { def f: T }; new T { def f = { return new T }}")) {
      case Error("return", NeedsResultType()) :: Nil =>
    }
  }

  def testTypeReturnWrongType() {
    assertMatches(messages("def f: A = { return new B }")) {
      case Error("new B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturnWrongUnit() {
    assertMatches(messages("def f: A = { return () }")) {
      case Error("()", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturnWrongTypeMultiple() {
    assertMatches(messages("def f: A = { if(1 > 2) return new B else return new B }")) {
      case Error("new B", TypeMismatch()) :: Error("new B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturnAndExpressionWrongType(){
    assertMatches(messages("def f: A = { if(1 > 2) return new B; new B }")) {
      case Error("new B", TypeMismatch()) :: Error("new B", TypeMismatch()) :: Nil =>
    }
  }

  //todo: requires Function1 trait in scope
  /*def testTypeExpressionImplicit() {
    assertMatches(messages("implicit def toA(b: B) = new A; def f: A = { new B }")) {
      case Nil =>
    }
  }*/

  //todo: requires Function1 trait in scope
  /*def testTypeReturnImplicit() {
    assertMatches(messages("implicit def toA(b: B) = new A; def f: A = { return new B }")) {
      case Nil =>
    }
  }*/

  def testUnresolvedTypeEmpty() {
    assertMatches(messages("def f: C = { }")) {
      case Nil =>
    }
  }

  def testUnresolvedTypeExpression() {
    assertMatches(messages("def f: C = { new A }")) {
      case Nil =>
    }
  }

  def testUnresolvedTypeReturn() {
    assertMatches(messages("def f: C = { return }")) {
      case Nil =>
    }
  }

  def testUnresolvedTypeReturnExpression() {
    assertMatches(messages("def f: C = { return new A }")) {
      case Nil =>
    }
  }

  def testUnresolvedExpression() {
     assertMatches(messages("def f: A = { new C }")) {
       case Nil =>
     }
   }

  def testReturnUnresolvedExpression() {
    assertMatches(messages("def f: A = { return new C }")) {
      case Nil =>
    }
  }

  def testUnresolvedBoth() {
    assertMatches(messages("def f: C = { new D }")) {
      case Nil =>
    }
  }

  def testUnresolvedBothReturn() {
    assertMatches(messages("def f: C = { return new D }")) {
      case Nil =>
    }
  }

  def testUnresolvedReference() {
    assertMatches(messages("def f: A = { foo }")) {
      case Nil =>
    }
  }

  def testUnitUnresolvedExpression() {
    assertMatches(messages("def f { new C }")) {
      case Nil =>
    }
  }

  def testUnitReturnUnresolvedExpression() {
    assertMatches(messages("def f { return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testTypeUnitUnresolvedExpression() {
    assertMatches(messages("def f: Unit = { new C }")) {
      case Nil =>
    }
  }

  def testTypeUnitReturnUnresolvedExpression() {
    assertMatches(messages("def f: Unit = { return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testAnyTypeUnresolvedExpression() {
    assertMatches(messages("def f: Any = { new C }")) {
      case Nil =>
    }
  }

  def testAnyTypeUnresolvedReturnExpression() {
    assertMatches(messages("def f: Any = { return new C }")) {
      case Nil =>
    }
  }

  def testNestedFunction() {
    val code = """
    def f1 = {
      def f2 { return }
      new A
    }"""
    assertMatches(messages(code)) {
      case Nil =>
    }
  }

   def testRecursiveUnit() {
    assertMatches(messages("def f { f }")) {
      case Nil =>
    }
  }

  def testRecursiveType() {
    assertMatches(messages("def f: A = { f }")) {
      case Nil =>
    }
  }

  def testRecursiveUnresolvedType() {
    assertMatches(messages("def f: C = { f }")) {
      case Nil =>
    }
  }

  def testRecursiveUnapplicable() {
    assertMatches(messages("def f = { f( new A ) }")) {
      case Error("f", Recursive()) :: Nil =>
    }
  }

  def testRecursive() {
    assertMatches(messages("def f = { f }")) {
      case Error("f", Recursive()) :: Nil =>
    }
  }

  def testRecursiveMultiple() {
    assertMatches(messages("def f = { f; f }")) {
      case Error("f", Recursive()) :: Error("f", Recursive()) :: Nil =>
    }
  }

  def testRecursiveParameter() {
    assertMatches(messages("def f(a: A) = { f(new A) }")) {
      case Error("f", Recursive()) :: Nil =>
    }
  }

  def testRecursiveWithInheritedResultType() {
    assertMatches(messages("trait T { def f: T }; new T { def f = { f }}")) {
      case Nil =>
    }
  }

  def testRecursiveAndNeedsResultType() {
    assertMatches(messages("def f = { f; return new A }")) {
      case Error("f", Recursive()) :: Error("return", NeedsResultType()) :: Nil =>
    }
  }

  def testRecursiveAndTypeMismatch() {
    assertMatches(messages("def f: A = { f; new B }")) {
      case Error("new B", TypeMismatch()) :: Nil =>
    }
  }

  def testRecursiveAndRedundantReturnData() {
    assertMatches(messages("def f { f; return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = new FunctionAnnotator() {}
    val parse: ScalaFile = (Header + code).parse

    val mock = new AnnotatorHolderMock(parse)

    parse.depthFirst.filterByType(classOf[ScFunctionDefinition]).foreach {
      annotator.annotateFunction(_, mock, typeAware = true)
    }

    mock.annotations
  }

  val TypeMismatch = ContainsPattern("Type mismatch")
  val RedundantReturnData = ContainsPattern("Unit result type")
  val NeedsResultType = ContainsPattern("has return statement")
  val Recursive = ContainsPattern("Recursive method")
}