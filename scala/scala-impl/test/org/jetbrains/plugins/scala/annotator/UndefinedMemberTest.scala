package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.template.UndefinedMember

/**
  * Created by mucianm on 23.03.16.
  */
class UndefinedMemberTest extends AnnotatorTestBase(UndefinedMember) {

  def testSCL8713(): Unit = {
    assertNothing(messages("object O { type T }"))
  }

}
