package org.jetbrains.sbt.lang.completion

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.completion3.ScalaCodeInsightTestBase
import org.jetbrains.sbt.language.SbtFileType

class SbtScalacOptionInsertHandlerTest extends ScalaCodeInsightTestBase {
  private val LOOKUP_ITEM = "-Yno-generic-signatures" // flag with multiple dashes
  private val RESULT_OPTION = s""""$LOOKUP_ITEM""""

  override protected def configureFromFileText(fileText: String): PsiFile =
    configureFromFileText(fileText, SbtFileType)

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
