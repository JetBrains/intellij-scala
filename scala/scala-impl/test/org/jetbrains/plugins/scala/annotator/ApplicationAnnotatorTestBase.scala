package org.jetbrains.plugins.scala
package annotator


import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.annotator.ScReferenceAnnotator
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
    val file = (Header + code).parse

    val mock = new AnnotatorHolderMock(file)

    val seq = file.depthFirst().instanceOf[ScClass]
    Compatibility.seqClass = seq
    try {
      // TODO use the general annotate() method
      file.depthFirst().instancesOf[ScReferenceAnnotator].foreach {
        _.annotateReference(mock)
      }

      file.depthFirst().instancesOf[ScMethodCall].foreach {
        _.annotate(mock, typeAware = true)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}
