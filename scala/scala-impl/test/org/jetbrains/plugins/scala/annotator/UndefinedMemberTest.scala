package org.jetbrains.plugins.scala.annotator

/**
  * Created by mucianm on 23.03.16.
  */
class UndefinedMemberTest extends AnnotatorTestBase {

  def testSCL8713(): Unit = {
    assertNothing(messages("object O { type T }"))
  }
}
