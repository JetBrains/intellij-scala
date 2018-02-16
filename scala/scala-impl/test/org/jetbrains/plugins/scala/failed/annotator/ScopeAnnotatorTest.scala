package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator.{AnnotatorPart, AnnotatorTestBase, ScopeAnnotator}
import org.jetbrains.plugins.scala.failed.annotator.ScopeAnnotatorTest.MyAnnotatorPart
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.junit.experimental.categories.Category

/**
  * User: Dmitry.Naydanov
  * Date: 25.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ScopeAnnotatorTest extends AnnotatorTestBase(new MyAnnotatorPart) {

  override protected def shouldPass: Boolean = false

  def testSCL7116(): Unit = assertNothing(
    messages(
      """
        |object SynoHilite {
        |    def foo(x: Option[String]): String = {
        |        println(x)
        |        x.get
        |    }
        |
        |    def foo(y: Option[Int]): Int = {
        |        println(y)
        |        y.get
        |    }
        |}
      """.stripMargin))

  def testSCL8633(): Unit = assertNothing(
    messages(
      """
        |object SCL8633 {
        |  def a(x: Int) = x
        |
        |  def <ref>a(x: => Int) = x
        |}
      """.stripMargin)
  )

  def testSCL3137(): Unit = assertNothing(
    messages(
      """
        |package a {
        |  class Foo
        |}
        |
        |package b {
        |  class Foo
        |}
        |
        |package c {
        |  trait Bar {
        |    def foo(foo: a.Foo) {}
        |    def foo(foo: b.Foo) {}
        |  }
        |}
      """.stripMargin)
  )
}

object ScopeAnnotatorTest {
  class MyAnnotatorPart extends AnnotatorPart[ScTemplateDefinition] {
    private val myAnnotator = new ScopeAnnotator {}

    override def annotate(element: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
      myAnnotator.annotateScope(element, holder)
    }
  }
}
