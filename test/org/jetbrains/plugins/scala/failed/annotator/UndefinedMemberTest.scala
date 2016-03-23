package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator.AnnotatorTestBase
import org.jetbrains.plugins.scala.annotator.template.UndefinedMember
import org.junit.experimental.categories.Category

/**
  * Created by mucianm on 23.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class UndefinedMemberTest extends AnnotatorTestBase(UndefinedMember) {

  def testSCL8713() = {
    assertNothing(messages("object O { type T }"))
  }

}
