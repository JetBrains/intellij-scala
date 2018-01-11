package org.jetbrains.plugins.scala.failed.typeInference

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility
import org.junit.experimental.categories.Category

/**
  * Created by mucianm on 23.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ConstructorParametersTest extends SimpleTestCase {

  override protected def shouldPass: Boolean = false

  def testSCL4504() = {
    assertNothing(messages(
      """
        |class B
        |trait C { val b: B}
        |class A(override implicit val b: B) extends C
        |//class A(implicit override val b: B) extends C
        |
        |implicit val b = new B
        |new A()
      """.stripMargin))
  }

  def testSCL11201() = {
    assertNothing(messages(
      """
        |object A extends Enumeration {
        |  val EXAMPLE = Value("example")
        |}
        |
        |class C[T](val enum: Enumeration { type Value = T } ) {
        |  def show(s: String): T = enum.withName(s).asInstanceOf[T]
        |}
        |
        |val b = new C(A)
      """.stripMargin))
  }


  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val annotator = new ConstructorAnnotator {}
    val file: ScalaFile = code.parse
    val mock = new AnnotatorHolderMock(file)

    val seq = file.depthFirst().findByType[ScClass]
    Compatibility.seqClass = seq

    try {
      file.depthFirst().filterByType[ScConstructor].foreach {
        annotator.annotateConstructor(_, mock)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}
