package org.jetbrains.plugins.scala.failed.resolve

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 24.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class FunScopeResolveTest extends SimpleTestCase {
  def testSCL5869(): Unit = {
    val code =
      """
        |class SCL5869(private[this] var param:Int) {
        |  def param():Int = this.param
        |}
      """.stripMargin
    assertNothing(messages(code))
  }

  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val annotator = new ScopeAnnotator {}
    val file = code.parse
    val mock = new AnnotatorHolderMock(file)
    val templateBody = file.depthFirst.find(_.isInstanceOf[ScTemplateBody]).get.asInstanceOf[ScTemplateBody]

    annotator.annotateScope(templateBody, mock)
    mock.annotations
  }
}
