package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturn
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

class FunctionAnnotatorTest extends AnnotatorSimpleTestCase {
  final val Header = "class A; class B\n"

  def testAssignReturn(): Unit = {
    assertMatches(messages("def f = { return }")) {
      case Error("return", NeedsResultType()) :: Nil =>
    }
  }

  def testAssignReturnExpression(): Unit = {
    assertMatches(messages("def f = { return new A }")) {
      case Error("return", NeedsResultType()) :: Nil =>
    }
  }

  def testInheritedTypeReturnType(): Unit = {
    assertMatches(messages("trait T { def f: T }; new T { def f = { return new T }}")) {
      case Error("return", NeedsResultType()) :: Nil =>
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

  def testRecursiveUnapplicable(): Unit = {
    assertMatches(messages("def f = { f( new A ) }")) {
      case Error("f", Recursive()) :: Nil =>
    }
  }

  def testRecursive(): Unit = {
    assertMatches(messages("def f = { f }")) {
      case Error("f", Recursive()) :: Nil =>
    }
  }

  def testRecursiveMultiple(): Unit = {
    assertMatches(messages("def f = { f; f }")) {
      case Error("f", Recursive()) :: Error("f", Recursive()) :: Nil =>
    }
  }

  def testRecursiveParameter(): Unit = {
    assertMatches(messages("def f(a: A) = { f(new A) }")) {
      case Error("f", Recursive()) :: Nil =>
    }
  }

  def testRecursiveWithInheritedResultType(): Unit = {
    assertMatches(messages("trait T { def f: T }; new T { def f = { f }}")) {
      case Nil =>
    }
  }

  def testRecursiveAndNeedsResultType(): Unit = {
    assertMatches(messages("def f = { f; return new A }")) {
      case Error("f", Recursive()) :: Error("return", NeedsResultType()) :: Nil =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = ScalaAnnotator.forProject
    val parse: ScalaFile = (Header + code).parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(parse)

    parse.depthFirst().filterByType[ScFunctionDefinition].foreach {
      annotator.annotateFunction(_)
    }

    mock.annotations
  }

  val NeedsResultType = ContainsPattern("has return statement")
  val Recursive = ContainsPattern("Recursive method")
}

class ReturnExpressionAnnotatorTest extends AnnotatorSimpleTestCase {
  final val Header = "class A; class B\n"

  def testTypeAbsolutelyEmpty(): Unit = {
    assertMatches(messages("def f: A = {}")) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }
  def testRecursiveAndTypeMismatch(): Unit = {
    assertMatches(messages("def f: A = { f; new B }")) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testAnyValNull(): Unit = {
    assertMatches(messages("def f: AnyVal = null")) {
      case Error("null", TypeMismatch()) :: Nil =>
    }
  }

  def testTry(): Unit = {
    assertMatches(messages(
      """
        |def myFunc(): Int = {
        |  try {
        |    val something = "some string"
        |    val someOtherValue = 123
        |  } catch {
        |    case e => throw e
        |  }
        |}
      """.stripMargin
    )) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeWrongExpression(): Unit = {
    assertMatches(messages("def f: A = { new B }")) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeWrongExpressionUnit(): Unit = {
    assertMatches(messages("def f: A = { () }")) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturnWrongType(): Unit = {
    assertMatches(messages("def f: A = { return new B }")) {
      case Error("new B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturnWrongUnit(): Unit = {
    assertMatches(messages("def f: A = { return () }")) {
      case Error("()", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturnWrongTypeMultiple(): Unit = {
    assertMatches(messages("def f: A = { if(1 > 2) return new B else return new B }")) {
      case Error("new B", TypeMismatch()) :: Error("new B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeEmpty(): Unit = {
    assertMatches(messages("def f: A = { }")) {
      case Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturnAndExpressionWrongType(): Unit = {
    assertMatches(messages("def f: A = { if(1 > 2) return new B; new B }")) {
      case Error("new B", TypeMismatch()) :: Error("}", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeReturn(): Unit = {
    assertMatches(messages("def f: A = { return }")) {
      case Error("return", TypeMismatch()) :: Nil =>
    }
  }

  def testRecursiveAndRedundantReturnData(): Unit = {
    assertMatches(messages("def f { f; return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testTypeUnitReturnType(): Unit = {
    assertMatches(messages("def f: Unit = { return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testTypeUnitReturnUnit(): Unit = {
    assertMatches(messages("def f: Unit = { return () }")) {
      case Warning("()", RedundantReturnData()) :: Nil =>
    }
  }

  def testUnitReturnType(): Unit = {
    assertMatches(messages("def f { return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testUnitReturnUnit(): Unit = {
    assertMatches(messages("def f { return () }")) {
      case Warning("()", RedundantReturnData()) :: Nil =>
    }
  }

  def testUnitReturnUnresolvedExpression(): Unit = {
    assertMatches(messages("def f { return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testTypeUnitReturnUnresolvedExpression(): Unit = {
    assertMatches(messages("def f: Unit = { return new A }")) {
      case Warning("new A", RedundantReturnData()) :: Nil =>
    }
  }

  def testUnitEmpty(): Unit = {
    assertMatches(messages("def f { }")) {
      case Nil =>
    }
  }

  def testUnitExpression(): Unit = {
    assertMatches(messages("def f { new A }")) {
      case Nil =>
    }
  }

  def testUnitExpressionUnit(): Unit = {
    assertMatches(messages("def f { () }")) {
      case Nil =>
    }
  }

  def testUnitReturn(): Unit = {
    assertMatches(messages("def f { return }")) {
      case Nil =>
    }
  }

  def testAssignNull(): Unit = {
    assertMatches(messages("def f = null")) {
      case Nil =>
    }
  }

  def testAssignEmpty(): Unit = {
    assertMatches(messages("def f = { }")) {
      case Nil =>
    }
  }

  def testAssignExpression(): Unit = {
    assertMatches(messages("def f = { new A }")) {
      case Nil =>
    }
  }
  def testTypeNull(): Unit = {
    assertMatches(messages("def f: A = null")) {
      case Nil =>
    }
  }

  def testTypeExpression(): Unit = {
    assertMatches(messages("def f: A = { new A }")) {
      case Nil =>
    }
  }

  def testTypeUnitEmpty(): Unit = {
    assertMatches(messages("def f: Unit = { }")) {
      case Nil =>
    }
  }

  def testTypeUnitExpression(): Unit = {
    assertMatches(messages("def f: Unit = { new A }")) {
      case Nil =>
    }
  }

  def testTypeUnitExpressionUnit(): Unit = {
    assertMatches(messages("def f: Unit = { () }")) {
      case Nil =>
    }
  }

  def testTypeUnitReturn(): Unit = {
    assertMatches(messages("def f: Unit = { return }")) {
      case Nil =>
    }
  }

  def testTypeReturnType(): Unit = {
    assertMatches(messages("def f: A = { return new A }")) {
      case Nil =>
    }
  }

  def testUnresolvedTypeEmpty(): Unit = {
    assertMatches(messages("def f: C = { }")) {
      case Nil =>
    }
  }

  def testUnresolvedTypeExpression(): Unit = {
    assertMatches(messages("def f: C = { new A }")) {
      case Nil =>
    }
  }

  def testUnresolvedTypeReturn(): Unit = {
    assertMatches(messages("def f: C = { return }")) {
      case Nil =>
    }
  }

  def testUnresolvedTypeReturnExpression(): Unit = {
    assertMatches(messages("def f: C = { return new A }")) {
      case Nil =>
    }
  }

  def testUnresolvedExpression(): Unit = {
    assertMatches(messages("def f: A = { new C }")) {
      case Nil =>
    }
  }

  def testReturnUnresolvedExpression(): Unit = {
    assertMatches(messages("def f: A = { return new C }")) {
      case Nil =>
    }
  }

  def testUnresolvedBoth(): Unit = {
    assertMatches(messages("def f: C = { new D }")) {
      case Nil =>
    }
  }

  def testUnresolvedBothReturn(): Unit = {
    assertMatches(messages("def f: C = { return new D }")) {
      case Nil =>
    }
  }

  def testUnitUnresolvedExpression(): Unit = {
    assertMatches(messages("def f { new C }")) {
      case Nil =>
    }
  }

  def testTypeUnitUnresolvedExpression(): Unit = {
    assertMatches(messages("def f: Unit = { new C }")) {
      case Nil =>
    }
  }

  def testAnyTypeUnresolvedExpression(): Unit = {
    assertMatches(messages("def f: Any = { new C }")) {
      case Nil =>
    }
  }

  def testAnyTypeUnresolvedReturnExpression(): Unit = {
    assertMatches(messages("def f: Any = { return new C }")) {
      case Nil =>
    }
  }

  def testNestedFunction(): Unit = {
    val code = """
    def f1 = {
      def f2 { return }
      new A
    }"""
    assertMatches(messages(code)) {
      case Nil =>
    }
  }

  def testRecursiveUnit(): Unit = {
    assertMatches(messages("def f { f }")) {
      case Nil =>
    }
  }

  def testRecursiveType(): Unit = {
    assertMatches(messages("def f: A = { f }")) {
      case Nil =>
    }
  }

  def testRecursiveUnresolvedType(): Unit = {
    assertMatches(messages("def f: C = { f }")) {
      case Nil =>
    }
  }

  val TypeMismatch = ContainsPattern("doesn't conform to expected type")
  val RedundantReturnData = ContainsPattern("Unit result type")

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = ScalaAnnotator.forProject
    val parse: ScalaFile = (Header + code).parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(parse)

    parse.depthFirst().filterByType[ScFunctionDefinition].foreach { fun =>
      val returnUsages = fun.returnUsages.flatMap {
        case r: ScReturn => Set(r) ++ r.expr
        case e => Set(e)
      }
      (fun.body.toSet ++ returnUsages).foreach(annotator.annotate)
    }

    mock.annotations.filter(_.message != null)
  }
}
