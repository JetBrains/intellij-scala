package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator.OverridingAnnotatorTestBase
import org.junit.experimental.categories.Category

/**
  * Created by mucianm on 22.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class OverridingAnnotatorTest extends OverridingAnnotatorTestBase {

  //#SCL-8577 overriding of inaccessible members with qualified private must show an error
  def testInaccessiblePrivateMembers(): Unit = {
    assert(
      messages(
        """
          |object FOO {
          |class A {
          |  private[A] def foo = 1
          |}
          |
          |class B extends A {
          |  override def foo: Int = 2
          |}}
        """.stripMargin
      ).nonEmpty)
  }

}
