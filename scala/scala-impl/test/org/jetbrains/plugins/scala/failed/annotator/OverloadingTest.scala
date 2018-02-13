package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.{PsiErrorElement, PsiReference}
import org.jetbrains.plugins.scala.annotator.AnnotatorHolderMock
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaLightCodeInsightFixtureTestAdapter}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeElement, ScTypeElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.{PerfCycleTests, ScalaBundle}
import org.junit.Assert._
import org.junit.experimental.categories.Category
import org.jetbrains.plugins.scala.annotator.Message

/**
  * User: Dmitry.Naydanov
  * Date: 23.03.16.
  * 
  *  
  */
@Category(Array(classOf[PerfCycleTests]))
class OverloadingTest extends ScalaLightCodeInsightFixtureTestAdapter with AssertMatches {

  override protected def shouldPass: Boolean = false

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

  protected def annotate(element: ScPatternDefinition, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    for (expr <- element.expr; element <- element.children.findByType[ScTypeElement])
      checkConformance(expr, element, holder)
  }

  private def checkConformance(expression: ScExpression, typeElement: ScTypeElement, holder: AnnotationHolder) {
    expression.getTypeAfterImplicitConversion().tr.foreach {actual =>
      val expected = typeElement.calcType
      if (!actual.conforms(expected)) {
        val expr = expression match {
          case b: ScBlockExpr => b.getRBrace.map(_.getPsi).getOrElse(b)
          case _ => expression
        }
        val (actualText, expText) = ScTypePresentation.different(actual, expected)
        val annotation = holder.createErrorAnnotation(expr,
          ScalaBundle.message("type.mismatch.found.required", actualText, expText))
        annotation.registerFix(ReportHighlightingErrorQuickFix)
      }
    }
  }

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

  def testSCL11684(): Unit = assertNothing(
    collectMessages(
      """
        |package pack1 {
        |
        |  trait Enclosure {
        |
        |    class A[T] {}
        |
        |    private [pack1] class B[T] extends A[T] {}
        |
        |    class C[T] extends B[T] {}
        |  }
        |}
        |
        |object Tester extends pack1.Enclosure {
        |
        |  trait Tweak[-S]
        |
        |  case class Sort[I](f: I => Int) extends Tweak[A[I]]
        |
        |  val x: Tweak[C[String]] = Sort[String](_.size)
        |}
      """.stripMargin)
  )
}
