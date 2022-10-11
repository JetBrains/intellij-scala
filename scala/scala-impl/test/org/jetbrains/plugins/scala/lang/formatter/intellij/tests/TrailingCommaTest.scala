package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.TrailingCommaMode
import org.jetbrains.plugins.scala.lang.formatting.settings.TrailingCommaPanel

class TrailingCommaTest extends AbstractScalaFormatterTestBase {

  override protected def setUp(): Unit = {
    super.setUp()
    val scalaSettings = getScalaSettings
    scalaSettings.TRAILING_COMMA_ARG_LIST_ENABLED = true
    scalaSettings.TRAILING_COMMA_PARAMS_ENABLED = true
    scalaSettings.TRAILING_COMMA_TUPLE_ENABLED = true
    scalaSettings.TRAILING_COMMA_TUPLE_TYPE_ENABLED = true
    scalaSettings.TRAILING_COMMA_PATTERN_ARG_LIST_ENABLED = true
    scalaSettings.TRAILING_COMMA_TYPE_PARAMS_ENABLED = true
    scalaSettings.TRAILING_COMMA_IMPORT_SELECTOR_ENABLED = true
  }

  def testTrailingCommaPanelIsInstantiatedNormally(): Unit = {
    assertNoThrowable(() => {
      val panel = new TrailingCommaPanel(getSettings)
      try {
        panel.getPanel
      } finally {
        panel.dispose()
      }
    })
  }

  def testKeep(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_KEEP
    val before =
      """import a.b.{
        |  c, d,
        |}
        |import a.b.{
        |  c, d
        |}
        |
        |List(1, 2, 3,)
        |List(1, 2, 3)
        |List(
        |  1,
        |  2,
        |)
        |List(
        |  1,
        |  2
        |)
        |
        |def foo(f: (Int, String,
        |  ) => Long) = ???
        |
        |def foo(f: (Int, String
        |  ) => Long) = ???
      """.stripMargin
    doTextTest(before)
  }

  private def testAddRemove(withoutComma: String, withComma: String): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    doTextTest(withoutComma, withComma)

    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_REMOVE_WHEN_MULTILINE
    doTextTest(withComma, withoutComma)
  }

  //
  // ADD / REMOVE
  //
  def testAddRemove_ArgumentsList(): Unit = testAddRemove(
    withoutComma =
      """foo(1, 2, 3
        |)""".stripMargin,
    withComma =
      """foo(1, 2, 3,
        |)""".stripMargin
  )

  def testAddRemove_MultipleArgumentsList(): Unit = testAddRemove(
    withoutComma =
      """foo(1, 2, 3
        |)(4, 5, 6
        |)""".stripMargin,
    withComma =
      """foo(1, 2, 3,
        |)(4, 5, 6,
        |)""".stripMargin
  )

  def testAddRemove_ArgumentsListWithComment(): Unit = testAddRemove(
    withoutComma =
      """List(
        |  1,
        |  2,
        |  3 //comment
        |)""".stripMargin,
    withComma =
      """List(
        |  1,
        |  2,
        |  3, //comment
        |)""".stripMargin
  )

  def testAddRemove_ConstructorCallMultipleArguments(): Unit = testAddRemove(
    withoutComma =
      """val a = new A(1, 2
        |)(3, 4
        |)""".stripMargin,
    withComma =
      """val a = new A(1, 2,
        |)(3, 4,
        |)""".stripMargin
  )

  def testAddRemove_MultipleParametersClauses(): Unit = testAddRemove(
    withoutComma =
      """def foo(
        |         x: Int,
        |         y: Int
        |       )(implicit
        |         w: String,
        |         u: Long
        |       ) = ???""".stripMargin,
    withComma =
      """def foo(
        |         x: Int,
        |         y: Int,
        |       )(implicit
        |         w: String,
        |         u: Long,
        |       ) = ???""".stripMargin
  )

  def testAddRemove_Tuple(): Unit = testAddRemove(
    withoutComma =
      """val tuple: (Int, Int) = (1, 2, 3
        |)""".stripMargin,
    withComma =
      """val tuple: (Int, Int) = (1, 2, 3,
        |)""".stripMargin
  )

  def testAddRemove_TupleType(): Unit = testAddRemove(
    withoutComma =
      """def foo(f: (Int, String
        |  ) => Long) = ???""".stripMargin,
    withComma =
      """def foo(f: (Int, String,
        |  ) => Long) = ???""".stripMargin
  )

  def testAddRemove_ImportStatementWithSelectors(): Unit = testAddRemove(
    withoutComma =
      """import org.example.{
        |  A, B,
        |  C, D
        |}""".stripMargin,
    withComma =
      """import org.example.{
        |  A, B,
        |  C, D,
        |}""".stripMargin
  )

  def testAddRemove_PatterArgumentList(): Unit = testAddRemove(
    withoutComma =
      """List(1, 2, 3) match {
        |  case List(1, 2
        |  ) => 1
        |}""".stripMargin,
    withComma =
      """List(1, 2, 3) match {
        |  case List(1, 2,
        |  ) => 1
        |}""".stripMargin
  )

  def testAddRemove_PatternTuple(): Unit = testAddRemove(
    withoutComma =
      """(Seq(): Any) match {
        |  case (1, 2, 3
        |    ) =>
        |  case _ =>
        |}""".stripMargin,
    withComma =
      """(Seq(): Any) match {
        |  case (1, 2, 3,
        |    ) =>
        |  case _ =>
        |}""".stripMargin
  )

  //
  // NOT ADD / NOT REMOVE
  //
  def testNotAdd_ParametersListWithVararg(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """def g(x: Int, y: Int*
        |     ) = 42
      """.stripMargin
    doTextTest(before)
  }

  def testNotAdd_ClassParametersListWithVararg(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """class A(x: Int, y: Int*
        |       )
      """.stripMargin
    doTextTest(before)
  }

  def testNotAdd_PatternArgumentListWithSequencePattern(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """List(1, 2, 3) match {
        |  case List(1, 2, _*
        |  ) => 1
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testNotAdd_PatternArgumentListWithNamedSequencePattern(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """List(1, 2, 3) match {
        |  case List(1, 2, _@_*
        |  ) => 1
        |}
      """.stripMargin
    doTextTest(before)
  }

  def testNotAdd_ArgumentListWithSequenceArgumentType(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """foo(1, 2, 3, seq: _*
        |)""".stripMargin
    doTextTest(before)
  }

  def testNotAdd_ImportStatement(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """import org.example1.A
        |import org.example2._
      """.stripMargin
    doTextTest(before)
  }

  def testNotAdd_IfCommaAlreadyExists(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """foo(1, 2, 3,
        |)""".stripMargin
    doTextTest(before)
  }

  def testNotAdd_IfCommaAlreadyExistsWithComment(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """foo(1, 2, 3, /*comment*/
        |)""".stripMargin
    doTextTest(before)
  }

  def testNotAdd_NotMultiline(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before ="""List(1, 2, 3)"""
    doTextTest(before)
  }

  def testNotAdd_NotMultilineWithComment(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before ="""List(1, 2, 3 /*comment*/)"""
    doTextTest(before)
  }

  def testNotAdd_ParameterBlockWithoutParenthesis(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """action {}
        |""".stripMargin
    doTextTest(before)
  }

  def testNotAdd_ParameterBlockWithoutParenthesis_1(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE
    val before =
      """val items = List[String]("One", "Two", "Three")
        |items.reduceLeft{_ + _}""".stripMargin
    val after =
      """val items = List[String]("One", "Two", "Three")
        |items.reduceLeft {
        |  _ + _
        |}
        |""".stripMargin
    doTextTest(before, after)
    doTextTest(after, after)
  }

  def testNotRemove_NotMultilineWithErrorComma(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_REMOVE_WHEN_MULTILINE
    val before ="""List(1, 2, 3,)"""
    doTextTest(before)
  }

  def testNotRemove_NotMultilineWithErrorCommaAndComment(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_REMOVE_WHEN_MULTILINE
    val before ="""List(1, 2, 3, /*comment*/)"""
    doTextTest(before)
  }

  def testNotRemove_NotMultilineWithMultipleTrailingCommasAndComment(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_REMOVE_WHEN_MULTILINE
    val before ="""List(1, 2, 3, /*comment*/ ,)""".stripMargin
    doTextTest(before)
  }

  def testNotRemove_WithMultipleTrailingCommasAndComment(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_REMOVE_WHEN_MULTILINE
    val before =
      """foo(1, 2, 3, /*comment*/ ,
        |)""".stripMargin
    doTextTest(before)
  }

  def testNotRemove_ImportStatementWithErrorComma(): Unit = {
    getScalaSettings.TRAILING_COMMA_MODE = TrailingCommaMode.TRAILING_COMMA_REMOVE_WHEN_MULTILINE
    val before =
      """import org.example.A,
        |import org.example._,
      """.stripMargin
    val after =
      """import org.example.A,
        |import org.example._,""".stripMargin
    doTextTest(before, after)
  }
}
