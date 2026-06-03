package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestFixture.TestPrepareResult
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

abstract class ScalaDocUnknownParameterInspectionTestBase extends ScalaInspectionTestBase {

  override protected val classOfInspection = classOf[ScalaDocUnknownParameterInspection]

  //noinspection NotImplementedCode (unused)
  override protected def description: String = ???

  override protected def descriptionMatches(s: String): Boolean = s != null
}

class ScalaDocUnknownParameterInspectionTest extends ScalaDocUnknownParameterInspectionTestBase {

  def testDeleteUnresolvedParamTag(): Unit = {
    testQuickFix(
      """/**
        | * @param unresolved
        | */
        |def foo = ???
        |""".stripMargin,
      """/**
        | * */
        |def foo = ???
        |""".stripMargin,
      "Remove @param unresolved"
    )
  }

  def testDeleteDuplicatedParamTag(): Unit = {
    testQuickFix(
      """/**
        | * @param param1 description 1
        | * @param param1 description 2
        | */
        |def foo(param1: String) = ???
        |""".stripMargin,
      """/**
        | * @param param1 description 1
        | * */
        |def foo(param1: String) = ???
        |""".stripMargin,
      "Remove @param param1"
    )
  }

  def testDeleteDuplicatedParamTag_InBackticks(): Unit = {
    testQuickFix(
      """/**
        | * @param param1 description 1
        | * @param `param1` description 2
        | */
        |def foo(param1: String) = ???
        |""".stripMargin,
      """/**
        | * @param param1 description 1
        | * */
        |def foo(param1: String) = ???
        |""".stripMargin,
      "Remove @param `param1`"
    )
  }

  def testDeleteEmptyParamTagOnVal(): Unit = {
    testQuickFix(
      """/**
        | * @param
        | */
        |val x = ???""".stripMargin,
      """/**
        | * */
        |val x = ???""".stripMargin,
      "Remove @param"
    )
  }

  def testNoWarning(): Unit =
    checkTextHasNoErrors(
      """/**
        | * For ScalaTest, Spec2, uTest
        | *
        | * @param yyy description yyyyyyy
        | */
        |@deprecated()
        |class YyyClass(yyy: String)""".stripMargin
    )

  def testNoWarning_AnnotationBeforeDoc(): Unit =
    checkTextHasNoErrors(
      """@deprecated()
        |/**
        | * For ScalaTest, Spec2, uTest
        | *
        | * @param xxx description xxxxxx
        | */
        |class XxxClass(xxx: String)""".stripMargin
    )


  def testDefineAndResolvedParamTags(): Unit = {
    checkTextHasNoErrors(
      """/**
        | * Some documentation
        | * @define foo bar
        | * @param bar some parameter
        | * @tparam T return type type
        | */
        |def foo[T](bar: Int): T""".stripMargin
    )
  }

  def testUnresolvedParamTag(): Unit = {
    assertTextHasMessages(
      """/**
        | * Some documentation
        | * @define foo bar
        | * @param unresolved some parameter
        | * @tparam T return type type
        | */
        |def foo[T](bar: Int): T
        |""".stripMargin,
      Seq(ExpectedHighlight(HighlightSeverity.WARNING, "unresolved", "Cannot resolve symbol unresolved")),
    )
  }

  private case class ExpectedHighlight(severity: HighlightSeverity, text: String, description: String)

  private def assertTextHasMessages(text: String, expectedHighlights: Seq[ExpectedHighlight]): Seq[HighlightInfo] = {
    configureByText(text)
    val actualHighlights = findMatchingHighlightings(text)
    val actualHighlightsShort = actualHighlights.map { h => ExpectedHighlight(h.getSeverity, h.getText, h.getDescription) }
    assertCollectionEquals(
      expectedHighlights,
      actualHighlightsShort
    )
    actualHighlights
  }

  def testUnresolvedTypeParamTag(): Unit = {
    assertTextHasMessages(
      """/**
        | * Some documentation
        | * @define foo bar
        | * @param bar some parameter
        | * @tparam unresolved return type type
        | */
        |def foo[T](bar: Int): T
        |""".stripMargin,
      Seq(ExpectedHighlight(HighlightSeverity.WARNING, "unresolved", "Cannot resolve symbol unresolved"))
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

    assertTextHasMessages(
      code,
      Seq(
        ExpectedHighlight(HighlightSeverity.WARNING, "@param", "Duplicate @param tag for parameter 'param2'"),
        ExpectedHighlight(HighlightSeverity.WARNING, "unknownParam1", "Cannot resolve symbol unknownParam1"),
        ExpectedHighlight(HighlightSeverity.WARNING, "unknownParam2", "Cannot resolve symbol unknownParam2"),
        ExpectedHighlight(HighlightSeverity.WARNING, "@tparam", "Duplicate @tparam tag for type parameter 'TypeParam2'"),
        ExpectedHighlight(HighlightSeverity.WARNING, "UnknownTypeParam1", "Cannot resolve symbol UnknownTypeParam1"),
        ExpectedHighlight(HighlightSeverity.WARNING, "UnknownTypeParam2", "Cannot resolve symbol UnknownTypeParam2"),
      )
    )
  }

  def testAllParametersShouldBeResolved_Function(): Unit = {
    checkTextHasNoErrors(
      """/**
        | * @param param1 dummy
        | * @tparam TypeParam1 dummy
        | */
        |def myFunction[TypeParam1](param1: AnyRef): Unit = ()
        |""".stripMargin
    )
  }

  def testAllParametersShouldBeResolved_ClassPrimaryConstructor(): Unit = {
    checkTextHasNoErrors(
      """/**
        | * @param param1 dummy
        | * @tparam TypeParam1 dummy
        | */
        |class MyClass[TypeParam1](param1: AnyRef)
        |""".stripMargin
    )
  }

  def testAllParametersShouldBeResolved_ClassSecondaryConstructor(): Unit = {
    checkTextHasNoErrors(
      """class MyClass() {
        |  /**
        |   * @param param1 dummy
        |   */
        |  def this(param1: AnyRef) = {
        |    this()
        |  }
        |}
        |""".stripMargin
    )
  }

  def testScaladocCantProcessTparamsForTypeAliasForNow(): Unit = {
    assertTextHasMessages(
      """/**
        | * @tparam TypeParam1 dummy
        | */
        |type MyTypeAlias[TypeParam1] = Nothing
        |""".stripMargin,
      Seq(
        ExpectedHighlight(HighlightSeverity.WEAK_WARNING, "@tparam", "Scaladoc can't process tparams for type alias now"),
      )
    )
  }
}

class ScalaDocUnknownParameterInspectionTest_Scala3 extends ScalaDocUnknownParameterInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testEnumWithCases(): Unit = {
    checkTextHasNoErrors(
      """/**
        | * @param myParameter parameter description
        | * @tparam MyTypeParameter type parameter description
        | */
        |enum TestEnum [MyTypeParameter](myParameter: Int) {
        |  /**
        |   * @param myParameterInner parameter description
        |   * @tparam MyTypeParameterInner type parameter description
        |   */
        |  case EnumMember[MyTypeParameterInner](myParameterInner: Int)
        |    extends TestEnum[MyTypeParameterInner](myParameterInner)
        |}
        |""".stripMargin
    )
  }
}