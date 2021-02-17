package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaSpacingTest extends AbstractScalaFormatterTestBase {

  // used to test that some formatting does not depend on some setting value
  private def doTextTestForAnyValue(before: String, after: String)(setters: (Boolean => Unit)*): Unit =
    forAnyValue(setters: _*) { () =>
      doTextTest(before, after)
    }

  private def doTextTestForAnyValue(before: String)(setters: (Boolean => Unit)*): Unit =
    forAnyValue(setters: _*) { () =>
      doTextTest(before)
    }

  // tries to set all possible values to some boolean settings and run the test
  // in theory there are 2^N combinations but we do not expect much setters in these unit tests
  private def forAnyValue(setters: (Boolean => Unit)*)(testBody: () => Any): Unit = setters.toList match {
    case Nil                    =>
      testBody()
    case setter :: otherSetters =>
      setter(false)
      forAnyValue(otherSetters: _*)(testBody)
      setter(true)
      forAnyValue(otherSetters: _*)(testBody)
  }

  def testSpaceBeforeTypeColon(): Unit = {
    val after = """val x: Int = 42"""
    doTextTest(after)
    doTextTest("""val x  : Int = 42""", after)
  }

  def testSpaceBeforeTypeColon_1(): Unit = {
    scalaSettings.SPACE_BEFORE_TYPE_COLON = true
    val after = """val x : Int = 42"""
    doTextTest(after)
    doTextTest("""val x: Int = 42""", after)
  }

  def testSpaceBeforeTypeColon_ShouldNotAffectTypeParameterContextBoundColon(): Unit = {
    val after = """def foo[T: X] = ???"""

    doTextTestForAnyValue(after)(scalaSettings.SPACE_BEFORE_TYPE_COLON = _)
  }

  def testRemoveSpaceIfColonCantStickToIdentifier(): Unit =
    doTextTest("""var Object : Symbol = _""", """var Object: Symbol = _""")

  def testDoNotRemoveSpaceIfColonCanStickToIdentifier(): Unit =
    doTextTest("""var Object_!= : Symbol = _""")

  //
  // TypeParamContextBounds
  //

  def testTypeParamContextBounds_SingleBound(): Unit =
    doTextTestForAnyValue(
      """class Test[M   :  E]""",
      """class Test[M: E]"""
    )(
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_REST_CONTEXT_BOUND_COLONS = _,
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK = _
    )

  def testTypeParamContextBounds_SingleBound_1(): Unit = {
    scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON = true

    doTextTestForAnyValue(
      """class Test[M   :  E]""",
      """class Test[M : E]"""
    )(
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_REST_CONTEXT_BOUND_COLONS = _,
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK = _
    )
  }

  def testTypeParamContextBounds_SingleBound_HigherKindedType(): Unit =
    doTextTestForAnyValue(
      """class Test[M[_]   :  E]""",
      """class Test[M[_] : E]"""
    )(
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON = _,
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_REST_CONTEXT_BOUND_COLONS = _
    )

  def testTypeParamContextBounds_SingleBound_HigherKindedType_1(): Unit = {
    scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK = false

    doTextTestForAnyValue(
      """class Test[M[_]   :  E]""",
      """class Test[M[_]: E]"""
    )(
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON = _,
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_REST_CONTEXT_BOUND_COLONS = _
    )
  }

  def testTypeParamContextBounds_MultipleBounds(): Unit = {
    doTextTestForAnyValue(
      """class Test[M   :  E:F   :G]""",
      """class Test[M: E : F : G]"""
    )(
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK = _
    )
  }

  def testTypeParamContextBounds_MultipleBounds_1(): Unit = {
    scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_REST_CONTEXT_BOUND_COLONS = false
    doTextTestForAnyValue(
      """class Test[M   :  E:F   :G]""",
      """class Test[M: E: F: G]"""
    )(
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK = _
    )
  }

  def testTypeParamContextBounds_MultipleBounds_2(): Unit = {
    scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON = true
    doTextTestForAnyValue(
      """class Test[M   :  E:F   :G]""",
      """class Test[M : E : F : G]"""
    )(scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK = _)
  }

  def testTypeParamContextBounds_MultipleBounds_3_HigherKindedType(): Unit = {
    doTextTestForAnyValue(
      """class Test[M[_]   :  E:F   :G]""",
      """class Test[M[_] : E : F : G]"""
    )(
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON = _
    )
  }

  def testTypeParamContextBounds_MultipleBounds_4_HigherKindedType_1(): Unit = {
    scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK = false
    doTextTestForAnyValue(
      """class Test[M[T]   :  E:F   :G]""",
      """class Test[M[T]: E : F : G]"""
    )(
      scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON = _
    )
  }

  def testTypeParamMixedBounds(): Unit =
    doTextTest(
      """class Test[M   <:   A   <%   B   <%   C   :   D   :  E]""",
      """class Test[M <: A <% B <% C : D : E]"""
    )

  def testTypeParamVariance(): Unit =
    doTextTest(
      """def foo[F[+A]] = ???
        |def foo[F[-A]] = ???
        |""".stripMargin
    )

  def testTypeParamVarianceWithUnderscore(): Unit =
    doTextTest(
      """def foo[F[+_]] = ???
        |def foo[F[-_]] = ???
        |""".stripMargin
    )

  // SCL-7690
  def testTypeParams_SpaceBeforeBrackets(): Unit = {
    val before =
      """def foo   [String]() = 42
        |def bar[A, B]: Int = 42
        |""".stripMargin

    // NOTE:
    // SPACE_BEFORE_TYPE_PARAMETER_LIST setting should not affect the result

    scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST = false
    doTextTestForAnyValue(
      before,
      """def foo[String]() = 42
        |def bar[A, B]: Int = 42
        |""".stripMargin
    )(commonSettings.SPACE_BEFORE_TYPE_PARAMETER_LIST = _)

    scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST = true
    doTextTestForAnyValue(before,
    """def foo [String]() = 42
      |def bar [A, B]: Int = 42
      |""".stripMargin
    )(commonSettings.SPACE_BEFORE_TYPE_PARAMETER_LIST = _)
  }

  def testTypeArgs_SpaceBeforeBrackets(): Unit = {
    val before =
      """foo   [String]()
        |bar[A, B]()
        |""".stripMargin

    commonSettings.SPACE_BEFORE_TYPE_PARAMETER_LIST = false
    doTextTestForAnyValue(
      before,
      """foo[String]()
        |bar[A, B]()
        |""".stripMargin
    )(scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST = _)

    commonSettings.SPACE_BEFORE_TYPE_PARAMETER_LIST = true
    doTextTestForAnyValue(
      before,
      """foo [String]()
        |bar [A, B]()
        |""".stripMargin
    )(scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST = _)
  }

  def testTypeArgs_SpaceBeforeBrackets_InMethodChain(): Unit = {
    val before =
      """val a = method1  [String]()
        |  .method2[String](
        |    "1", "2"
        |  )
        |  .method3   [String](
        |    "3", "4"
        |  )""".stripMargin

    // NOTE:
    // SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST settings should not affect the result

    commonSettings.SPACE_BEFORE_TYPE_PARAMETER_LIST = false
    doTextTestForAnyValue(
      before,
      """val a = method1[String]()
        |  .method2[String](
        |    "1", "2"
        |  )
        |  .method3[String](
        |    "3", "4"
        |  )
        |""".stripMargin
    )(scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST = _)

    commonSettings.SPACE_BEFORE_TYPE_PARAMETER_LIST = true
    doTextTestForAnyValue(
      before,
      """val a = method1 [String]()
        |  .method2 [String](
        |    "1", "2"
        |  )
        |  .method3 [String](
        |    "3", "4"
        |  )
        |""".stripMargin
    )(scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST = _)
  }
}