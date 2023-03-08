package org.jetbrains.sbt.lang.completion

class SbtScalacOptionInsertHandlerTest extends SbtCompletionTestBase {
  private val LOOKUP_ITEM = "-Yno-generic-signatures" // flag with multiple dashes
  private val RESULT_OPTION = s""""$LOOKUP_ITEM""""

  private val LOOKUP_ITEM_WITH_SEPARATE_ARG = "-classpath"
  private val RESULT_OPTION_WITH_SEPARATE_ARG = s"""Seq("$LOOKUP_ITEM_WITH_SEPARATE_ARG", ".")"""

  def testTopLevel_Single_OutsideOfStringLiteral_AfterParenthesisedExpr(): Unit = doCompletionTest(
    fileText =
      s"""
         |(scalacOptions) += no$CARET
         |""".stripMargin,
    resultText =
      s"""
         |(scalacOptions) += $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_InsideOfStringLiteral_AfterParenthesisedExpr(): Unit = doCompletionTest(
    fileText =
      s"""
         |(scalacOptions) += "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |(scalacOptions) += $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_OutsideOfStringLiteral_AfterParenthesisedExpr2(): Unit = doCompletionTest(
    fileText =
      s"""
         |(ThisBuild / scalacOptions) += no$CARET
         |""".stripMargin,
    resultText =
      s"""
         |(ThisBuild / scalacOptions) += $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_InsideOfStringLiteral_AfterParenthesisedExpr2(): Unit = doCompletionTest(
    fileText =
      s"""
         |(ThisBuild / scalacOptions) += "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |(ThisBuild / scalacOptions) += $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_OutsideOfStringLiteral_AfterDeepParenthesisedExpr(): Unit = doCompletionTest(
    fileText =
      s"""
         |(((((Global / scalacOptions))))) += no$CARET
         |""".stripMargin,
    resultText =
      s"""
         |(((((Global / scalacOptions))))) += $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_InsideOfStringLiteral_AfterDeepParenthesisedExpr(): Unit = doCompletionTest(
    fileText =
      s"""
         |(((((Global / scalacOptions))))) += "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |(((((Global / scalacOptions))))) += $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_OutsideOfStringLiteral_WithoutSpaces(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions+=no$CARET
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions+=$RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_InsideOfStringLiteral_WithoutSpaces(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions+="$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions+=$RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions += $CARET
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions += $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions += "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions += $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_OutsideOfStringLiteral_-=(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions -= no$CARET
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions -= $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_InsideOfStringLiteral_-=(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions -= "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions -= $RESULT_OPTION
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SingleToSeq_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions += $CARET
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions ++= $RESULT_OPTION_WITH_SEPARATE_ARG
         |""".stripMargin,
    item = LOOKUP_ITEM_WITH_SEPARATE_ARG
  )

  def testTopLevel_SingleToSeq_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions += "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions ++= $RESULT_OPTION_WITH_SEPARATE_ARG
         |""".stripMargin,
    item = LOOKUP_ITEM_WITH_SEPARATE_ARG
  )

  def testTopLevel_SingleToSeq_OutsideOfStringLiteral_-=(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions -= $CARET
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions --= $RESULT_OPTION_WITH_SEPARATE_ARG
         |""".stripMargin,
    item = LOOKUP_ITEM_WITH_SEPARATE_ARG
  )

  def testTopLevel_SingleToSeq_InsideOfStringLiteral_-=(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions -= "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions --= $RESULT_OPTION_WITH_SEPARATE_ARG
         |""".stripMargin,
    item = LOOKUP_ITEM_WITH_SEPARATE_ARG
  )

  def testTopLevel_SeqOneLine_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions ++= Seq($CARET)
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions ++= Seq($RESULT_OPTION)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions ++= Seq("$CARET")
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions ++= Seq($RESULT_OPTION)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_OutsideOfStringLiteral_--=(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions --= Seq($CARET)
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions --= Seq($RESULT_OPTION)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_InsideOfStringLiteral_--=(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions --= Seq("$CARET")
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions --= Seq($RESULT_OPTION)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_OutsideOfStringLiteral_:=(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions := Seq($CARET)
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions := Seq($RESULT_OPTION)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_InsideOfStringLiteral_:=(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions := Seq("$CARET")
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions := Seq($RESULT_OPTION)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineFirst_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions ++= Seq(
         |  $CARET
         |)
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions ++= Seq(
         |  $RESULT_OPTION
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineFirst_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions ++= Seq(
         |  "$CARET"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions ++= Seq(
         |  $RESULT_OPTION
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineSecond_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions ++= Seq(
         |  "-foo-bar-baz",
         |  $CARET
         |)
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions ++= Seq(
         |  "-foo-bar-baz",
         |  $RESULT_OPTION
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineSecond_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |scalacOptions ++= Seq(
         |  "-foo-bar-baz",
         |  "$CARET"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |scalacOptions ++= Seq(
         |  "-foo-bar-baz",
         |  $RESULT_OPTION
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_Single_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions += $CARET
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions += $RESULT_OPTION
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_Single_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions += "$CARET"
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions += $RESULT_OPTION
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SingleToSeq_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions += $CARET
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= $RESULT_OPTION_WITH_SEPARATE_ARG
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM_WITH_SEPARATE_ARG
  )

  def testInProjectSettings_SingleToSeq_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions += "$CARET"
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= $RESULT_OPTION_WITH_SEPARATE_ARG
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM_WITH_SEPARATE_ARG
  )

  def testInProjectSettings_SeqOneLine_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq($CARET)
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq($RESULT_OPTION)
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqOneLine_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq("$CARET")
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq($RESULT_OPTION)
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineFirst_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq(
         |      $CARET
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq(
         |      $RESULT_OPTION
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineFirst_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq(
         |      "$CARET"
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq(
         |      $RESULT_OPTION
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineSecond_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq(
         |      "-foo-bar-baz",
         |      $CARET
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq(
         |      "-foo-bar-baz",
         |      $RESULT_OPTION
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineSecond_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq(
         |      "-foo-bar-baz",
         |      "$CARET"
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    scalacOptions ++= Seq(
         |      "-foo-bar-baz",
         |      $RESULT_OPTION
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

}
