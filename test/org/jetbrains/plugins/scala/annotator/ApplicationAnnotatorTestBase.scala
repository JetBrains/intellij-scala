package org.jetbrains.plugins.scala
package annotator


import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility

/**
  * Created by kate on 3/24/16.
  */
trait ApplicationAnnotatorTestBase extends SimpleTestCase{
  final val Header = """
  class Seq[+A]
  object Seq { def apply[A](a: A) = new Seq[A] }
  class A; class B;
  object A extends A; object B extends B
                     """


  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = new ApplicationAnnotator() {}
    val file = (Header + code).parse

    val mock = new AnnotatorHolderMock(file)

    val seq = file.depthFirst.findByType(classOf[ScClass])
    Compatibility.seqClass = seq
    try {
      file.depthFirst.filterByType(classOf[ScReferenceElement]).foreach {
        annotator.annotateReference(_, mock)
      }

      file.depthFirst.filterByType(classOf[ScMethodCall]).foreach {
        annotator.annotateMethodInvocation(_, mock)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}
