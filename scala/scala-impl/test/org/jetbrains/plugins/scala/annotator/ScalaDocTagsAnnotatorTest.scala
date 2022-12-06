package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScalaDocTagsAnnotatorTest extends SimpleTestCase {

  private def textWithComment(paramTag: String = "bar", typeParamTag: String = "T"): String =
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

  def testDefineAndResolvedParamTags(): Unit = {
    assertNothing(messages(textWithComment()))
  }

  def testUnresolvedParamTag(): Unit = {
    assertMessages(messages(textWithComment(paramTag = "baz")))(
      Warning("baz", "Cannot resolve symbol baz")
    )
  }

  def testUnresolvedTypeParamTag(): Unit = {
    assertMessages(messages(textWithComment(typeParamTag = "A")))(
      Warning("A", "Cannot resolve symbol A")
    )
  }

  def testUnresolvedParamAndTypeParamTags(): Unit = {
    val code =
      """class Wrapper {
        |  /**
        |   * @param param1        dummy
        |   * @param param2        dummy
        |   * @param param2        dummy (DUPLICATED PARAM VALUE)
        |   * @param unknownParam1 dummy
        |   * @param unknownParam2 dummy
        |   *
        |   * @tparam TypeParam1        dummy
        |   * @tparam TypeParam2        dummy
        |   * @tparam TypeParam2        dummy (DUPLICATED TYPE PARAM VALUE)
        |   * @tparam UnknownTypeParam1 dummy
        |   * @tparam UnknownTypeParam2 dummy
        |   */
        |  def bar[TypeParam1, TypeParam2](param1: AnyRef, param2: AnyRef): Unit = ()
        |}
        |""".stripMargin

    assertMessages(messages(code))(
      Warning("unknownParam1", "Cannot resolve symbol unknownParam1"),
      Warning("unknownParam2", "Cannot resolve symbol unknownParam2"),
      Warning("UnknownTypeParam1", "Cannot resolve symbol UnknownTypeParam1"),
      Warning("UnknownTypeParam2", "Cannot resolve symbol UnknownTypeParam2"),
    )
  }

  def testAllParametersShouldBeResolved_Function(): Unit = {
    assertNothing(messages(
      """/**
        | * @param param1 dummy
        | * @tparam TypeParam1 dummy
        | */
        |def myFunction[TypeParam1](param1: AnyRef): Unit = ()
        |""".stripMargin
    ))
  }

  def testAllParametersShouldBeResolved_ClassPrimaryConstructor(): Unit = {
    assertNothing(messages(
      """/**
        | * @param param1 dummy
        | * @tparam TypeParam1 dummy
        | */
        |class MyClass[TypeParam1](param1: AnyRef)
        |""".stripMargin
    ))
  }

  def testAllParametersShouldBeResolved_ClassSecondaryConstructor(): Unit = {
    assertNothing(messages(
      """class MyClass() {
        |  /**
        |   * @param param1 dummy
        |   */
        |  def this(param1: AnyRef) = {
        |    this()
        |  }
        |}
        |""".stripMargin
    ))
  }

  def testAllParametersShouldBeResolved_TypeAlias(): Unit = {
    assertNothing(messages(
      """/**
        | * @tparam TypeParam1 dummy
        | */
        |type MyTypeAlias[TypeParam1] = Nothing
        |""".stripMargin
    ))
  }

  private def messages(code: String): List[Message] = {
    val annotator = new ScalaAnnotator()
    val file: ScalaFile = code.parse
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotator.annotate)
    mock.annotations
  }
}
