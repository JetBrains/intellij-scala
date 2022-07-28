package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class NeedsToBeMixinTest extends AnnotatorTestBase[ScTemplateDefinition] {
  def testProblem(): Unit = {
    val message = ScalaBundle.message("mixin.required", "Class", "C", "base", "T")

    assertMatches(messages(
      """
        |trait Base {
        |  def base: Int
        |}
        |trait T extends Base {
        |  abstract override def base: Int = 1
        |}
        |class C extends T
      """.stripMargin
    )) {
      case Error("C", `message`) :: Nil =>
    }
  }

  def testFine(): Unit = {
    assertNothing(messages(
      """
        |trait Base {
        |  def base: Int
        |}
        |trait T extends Base {
        |  abstract override def base: Int = 1
        |}
        |trait Impl {
        |  def base: Int = 2
        |}
        |class C extends Impl with T
      """.stripMargin
    ))
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateNeedsToBeMixin(element)
}
