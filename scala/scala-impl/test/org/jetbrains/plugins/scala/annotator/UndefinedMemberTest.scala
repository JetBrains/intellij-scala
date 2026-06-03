package org.jetbrains.plugins.scala.annotator

class UndefinedMemberTest extends AnnotatorTestBase {

  def testSCL8713(): Unit = {
    assertNothing(messages("object O { type T }"))
  }
}
