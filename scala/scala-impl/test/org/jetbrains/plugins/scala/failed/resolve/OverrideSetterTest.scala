package org.jetbrains.plugins.scala
package failed.resolve

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScAssignmentAnnotator
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Message}
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class OverrideSetterTest extends SimpleTestCase {

  override protected def shouldPass: Boolean = false

  // Setter method not being referenced when assigning to a var
  def testSCL6054(): Unit = {
    val messages1 = messages(
      """
        |trait Foo {
        |  class A
        |  class B
        |  var foo:A
        |  def foo_=(f: B)
        |  foo = new B
        |}
      """.stripMargin)
    assertNothing(messages1)
  }

  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val file = code.parse
    val assignment = file.depthFirst().find(_.isInstanceOf[ScAssignment]).get.asInstanceOf[ScAssignment]

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)
    ScAssignmentAnnotator.annotate(assignment, typeAware = true)
    mock.annotations
  }
}
