package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.psi.{PsiErrorElement, PsiReference}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Message, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeElement, ScTypeElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.TypePresentation
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.junit.Assert._

/**
  * User: Dmitry.Naydanov
  * Date: 23.03.16.
  * 
  *  
  */
abstract class OverloadingTestBase extends ScalaLightCodeInsightFixtureTestAdapter with MatcherAssertions {

  //TODO this class contains a fair amount of a copy-paste code, however refactoring isn't practical here as the class is to be removed soon 

  import org.jetbrains.plugins.scala.extensions._

  protected def collectMessages(fileText: String): Option[List[Message]] = {
    myFixture.configureByText("dummy.scala", fileText)
    val file = myFixture.getFile
    val mock = new AnnotatorHolderMock(file)

    val errorElements = file.depthFirst().filterByType[PsiErrorElement].map(_.getText).toList
    if (shouldPass) assertEquals(Nil, errorElements)
    else if (errorElements.nonEmpty) return None

    val unresolvedElements = file.depthFirst().filterByType[PsiReference].
      filter(_.resolve == null).map(_.getElement.getText).toList
    if (shouldPass) assertEquals(Nil, unresolvedElements)
    else if (unresolvedElements.nonEmpty) return None

    file.depthFirst().foreach {
      case it: ScPatternDefinition => annotate(it, mock, typeAware = true)
      case _ =>
    }

    Some(mock.annotations)
  }

  protected def annotate(element: ScPatternDefinition, holder: ScalaAnnotationHolder, typeAware: Boolean): Unit = {
    for (expr <- element.expr; element <- element.children.findByType[ScTypeElement])
      checkConformance(expr, element, holder)
  }

  // TODO Why do we have this custom _implementation_ in a _test_?
  // TODO Use TypeMismatchError.register
  private def checkConformance(expression: ScExpression, typeElement: ScTypeElement, holder: ScalaAnnotationHolder): Unit = {
    expression.getTypeAfterImplicitConversion().tr.foreach { actual =>
      val expected = typeElement.calcType
      if (!actual.conforms(expected)) {
        val expr = expression match {
          case b: ScBlockExpr => b.getRBrace.getOrElse(b)
          case _ => expression
        }
        val (actualText, expText) = TypePresentation.different(actual, expected)(expr)
        val annotation = holder.createErrorAnnotation(expr,
          ScalaBundle.message("type.mismatch.found.required", actualText, expText))
        annotation.registerFix(ReportHighlightingErrorQuickFix)
      }
    }
  }
}

class OverloadingTest extends OverloadingTestBase {
  override protected def shouldPass: Boolean = false

  def testSCL9908(): Unit = assertNothing(
    collectMessages(
      """
        |class Test { 
        |  def foo(s: String, args: Any*) = println("foo(s, args)") 
        |  def foo(x: Any) = println("foo(x)") 
        | 
        |  def func(args: Array[String]) = { 
        |    foo("Hello") // red code; 'foo(s, args)' with scalac 
        |  } 
        |}
      """.stripMargin)
  )

  def testSCL7442(): Unit = assertNothing(
    collectMessages(
      """
        |class Test { 
        |  def set(value: Any) : Unit = {
        |    val (a, b, c, d) = value.asInstanceOf[(Int, Int, Int, Int)]
        |    set(a, b, c, d)
        |  }
        |  def set(aValue: Int, bValue: Int, cValue: Int, dValue: Int) = {
        |    //...
        |  }
        |  (set _).tupled((1, 2, 3, 4))
        |}
      """.stripMargin)
  )

  def testSCL10158(): Unit = assertNothing(
    collectMessages(
      """
        |class Test { 
        |  val lock = new AnyRef
        |  class Test {
        |    def run: Unit = this.synchronized(println("sync"))
        |    def synchronized[T](exec: => T): Unit = lock.synchronized(exec)
        |  }
        |}
      """.stripMargin)
  )

  def testSCL10183(): Unit = assertNothing(
    collectMessages(
      """
        |class MyClass {
        |  def foo[T](): T = ???
        |
        |  val value = foo[MyTrait]
        |  value.get _
        |}
        |
        |trait MyTrait {
        |  def get() = ???
        |  def get[A](arg: Any => Any) = ???
        |}
      """.stripMargin)
  )

  def testSCL10295(): Unit = assertNothing(
    collectMessages(
      """
        |import java.lang.reflect.Field
        |import scala.collection.mutable
        |
        |class Test {
        |
        |  def instanceFieldsOf(v: AnyRef): Array[Field] = ???
        |  def instanceFieldsOf(v: AnyRef,
        |                       cache: mutable.Map[Class[_], Array[Field]],
        |                       newFieldsHandler: Field => Unit = v => ())
        |  : Array[Field] = ???
        |
        |  def valueAndInstanceFieldTuplesOf(v: AnyRef,
        |                                    cache: mutable.Map[Class[_], Array[Field]],
        |                                    newFieldsHandler: Field => Unit = v => ())
        |  : Stream[(AnyRef, Field)] = {
        |    val fields: Array[Field] = this.instanceFieldsOf(v, cache, newFieldsHandler)
        |    fields.toStream.map { f => (f.get(v), f) }
        |  }
        |}
      """.stripMargin)
  )
}
