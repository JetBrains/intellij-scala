package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScalaDocTagsAnnotatorTest extends SimpleTestCase {

  private def textWithComment(paramTag: String = "bar", typeParamTag: String = "T"): String = {
    s"""
      |trait A {
      |
      |  /** Some documentation
      |  * @define foo bar
      |  * @param $paramTag some parameter
      |  * @tparam $typeParamTag return type type
      |  */
      |  def foo[T](bar: Int): T
      |}
    """.stripMargin
  }

  def testDefineAndResolvedParamTags(): Unit = assertNothing(messages(textWithComment()))

  def testUnresolvedParamTag(): Unit = assertMessages(messages(textWithComment(paramTag = "baz")))(
    Warning("baz", "Cannot resolve symbol baz")
  )

  def testUnresolvedTypeParamTag(): Unit = assertMessages(messages(textWithComment(typeParamTag = "A")))(
    Warning("A", "Cannot resolve symbol A")
  )

  private def messages(code: String): List[Message] = {
    val annotator = ScalaAnnotator.forProject
    val file: ScalaFile = code.parse
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotator.annotate)
    mock.annotations
  }
}
