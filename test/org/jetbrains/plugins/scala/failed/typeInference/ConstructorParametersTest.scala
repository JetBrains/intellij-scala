package org.jetbrains.plugins.scala.failed.typeInference

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility
import org.junit.experimental.categories.Category

/**
  * Created by mucianm on 23.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ConstructorParametersTest extends SimpleTestCase {

  def testSCL4504() = {
    assertNothing(messages(
      """
        |class B
        |trait C { val b: B}
        |class A(override implicit val b: B) extends C
        |//class A(implicit override val b: B) extends C
        |
        |implicit val b = new B
        |new A()
      """.stripMargin))
  }


  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val annotator = new ConstructorAnnotator {}
    val file: ScalaFile = code.parse
    val mock = new AnnotatorHolderMock(file)

    val seq = file.depthFirst.findByType(classOf[ScClass])
    Compatibility.seqClass = seq

    try {
      file.depthFirst.filterByType(classOf[ScConstructor]).foreach {
        annotator.annotateConstructor(_, mock)
      }

      mock.annotations
    }
    finally {
      Compatibility.seqClass = None
    }
  }
}
