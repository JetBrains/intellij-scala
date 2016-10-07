package org.jetbrains.plugins.scala.failed.resolve

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, AssignmentAnnotator, Message}
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignStmt
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 23.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class OverrideSetterTest extends SimpleTestCase {

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
    val assignment = file.depthFirst.find(_.isInstanceOf[ScAssignStmt]).get.asInstanceOf[ScAssignStmt]

    val annotator = new AssignmentAnnotator {}
    val mock = new AnnotatorHolderMock(file)
    annotator.annotateAssignment(assignment, mock, advancedHighlighting = true)
    mock.annotations
  }
}
