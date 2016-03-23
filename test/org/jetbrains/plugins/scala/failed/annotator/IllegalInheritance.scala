package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.annotator.AnnotatorTestBase
import org.jetbrains.plugins.scala.annotator.template.IllegalInheritance

/**
  * Created by kate on 3/23/16.
  */
class IllegalInheritance extends AnnotatorTestBase(IllegalInheritance){
  def testSCL8628(): Unit = {
    assertNothing(
      messages(
        """
          trait Engine[E <: Engine[E]] {
          |  type IndexType[T] <: Index[T, E]
          |}
          |
          |trait Index[T, E <: Engine[E]] {
          |  self: E#IndexType[T] =>
          |}
          |
          |trait IndexFoo[T, E <: Engine[E]] extends Index[T, E] {
          |  self: E#IndexType[T] =>
          |}
        """.stripMargin
      ))
  }
}
