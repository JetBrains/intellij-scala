package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.annotator.{Message, OverridingAnnotatorTestBase}

/**
  * Created by mucianm on 22.03.16.
  */
class OverridingAnnotatorTest extends OverridingAnnotatorTestBase {

  override protected def shouldPass: Boolean = false

  //#SCL-8577 overriding of inaccessible members with qualified private must show an error
  def testInaccessiblePrivateMembers(): Unit = {
    assertMatches(
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
      )) {
      case l: List[Message] if l.nonEmpty =>
    }
  }

  def testScl6809(): Unit = {
    assertNothing(
      messages(
        """import java.{util => ju}
          |
          |abstract class CollectionToArrayBug[E <: AnyRef](collection: ju.Collection[E])
          |  extends ju.Collection[E]
          |{
          |  def toArray[T](a: Array[T]): Array[T] = ???
          |  override def toArray[T](a: Array[T with AnyRef]): Array[T with AnyRef] = ???
          |}
        """.stripMargin))
  }
}
