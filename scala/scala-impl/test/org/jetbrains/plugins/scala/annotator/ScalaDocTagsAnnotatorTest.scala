package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.TypecheckerTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScalaDocTagsAnnotatorTest extends ScalaHighlightingTestBase {

  def testDefineAndResolvedParamTags(): Unit = {
    assertNoMessages(
      """/**
        | * Some documentation
        |
        | * @define macroKey my macro value
        | * @param bar some parameter
        | * @tparam T return type type
        | */
        |def foo[T](bar: Int): T""".stripMargin
    )
  }

  //NOTE: unresolved parameters is tested in org.jetbrains.plugins.scala.codeInspection.scaladoc.ScalaDocUnknownParameterInspectionTest
  //it's not responsibility of the annotator
  def testUnresolvedParamTags(): Unit = {
    assertNoMessages(
      """/**
        | * Some documentation
        |
        | * @define macroKey my macro value
        | * @param unresolvedParameter some parameter
        | * @tparam unresolvedTypeParameter return type type
        | */
        |def foo[T](bar: Int): T
        |""".stripMargin,
    )
  }
  def testResolvedThrowsException(): Unit = {
    assertNoMessages(
      """/**
        | * @throws RuntimeException dummy description
        | */
        |type MyTypeAlias[TypeParam1] = Nothing
        |""".stripMargin
    )
  }

  def testUnresolvedThrowsException(): Unit = {
    assertMessages(
      """/**
        | * @throws UnknownException42 dummy description
        | */
        |def foo = null
        |""".stripMargin,
      Warning("UnknownException42", "Cannot resolve symbol UnknownException42")
    )
  }
}
