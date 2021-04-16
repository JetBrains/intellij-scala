package org.jetbrains.plugins.scala.failed.typeInference

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.annotator.element.ScConstructorInvocationAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility

/**
  * Created by mucianm on 23.03.16.
  */
class ConstructorParametersTest extends SimpleTestCase {

  override protected def shouldPass: Boolean = false

  def testSCL11201(): Unit = {
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
    val file: ScalaFile = code.parse
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    val seq = file.depthFirst().findByType[ScClass]
    Compatibility.seqClass = seq

    try {
      file.depthFirst().filterByType[ScConstructorInvocation].foreach {
        ScConstructorInvocationAnnotator.annotate(_)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}
