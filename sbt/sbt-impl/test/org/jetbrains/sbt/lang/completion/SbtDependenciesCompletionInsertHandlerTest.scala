package org.jetbrains.sbt.lang.completion

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.sbt.language.SbtFileType

class SbtDependenciesCompletionInsertHandlerTest extends SbtCompletionTestBase {
  private val GROUP_ID = "org.scalatest"
  private val ARTIFACT_ID = "scalatest"
  private val RENDERING_PLACEHOLDER = "Sbtzzz"
  private val LOOKUP_ITEM = s"$GROUP_ID:$ARTIFACT_ID:$RENDERING_PLACEHOLDER"
  private val RESULT_DEPENDENCY = s""""$GROUP_ID" % "$ARTIFACT_ID" % "$CARET""""

  def testTopLevel_Single_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies += $CARET
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += $RESULT_DEPENDENCY
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies += "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += $RESULT_DEPENDENCY
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_CompleteArtifact_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies += "$GROUP_ID" % $CARET
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += $RESULT_DEPENDENCY
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_CompleteArtifact_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies += "$GROUP_ID" % "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += $RESULT_DEPENDENCY
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies += "$GROUP_ID" % $CARET % "0.0.1"
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += $RESULT_DEPENDENCY
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_Single_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies += "$GROUP_ID" % "$CARET" % "0.0.1"
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += $RESULT_DEPENDENCY
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq($CARET)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("$CARET")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_CompleteArtifact_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % $CARET)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_CompleteArtifact_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % "$CARET")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % $CARET % "0.0.1")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqOneLine_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % "$CARET" % "0.0.1")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineFirst_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  $CARET
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineFirst_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$CARET"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineFirst_CompleteArtifact_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$GROUP_ID" % $CARET
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineFirst_CompleteArtifact_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$GROUP_ID" % "$CARET"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$GROUP_ID" % $CARET % "0.0.1"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$GROUP_ID" % "$CARET" % "0.0.1"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineSecond_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  $CARET
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineSecond_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  "$CARET"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineSecond_CompleteArtifact_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  "$GROUP_ID" % $CARET
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineSecond_CompleteArtifact_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  "$GROUP_ID" % "$CARET"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  "$GROUP_ID" % $CARET % "0.0.1"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  $RESULT_DEPENDENCY
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testTopLevel_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  "$GROUP_ID" % "$CARET" % "0.0.1"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  $RESULT_DEPENDENCY
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
         |    libraryDependencies += $CARET
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += $RESULT_DEPENDENCY
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
         |    libraryDependencies += "$CARET"
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += $RESULT_DEPENDENCY
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_Single_CompleteArtifact_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += "$GROUP_ID" % $CARET
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += $RESULT_DEPENDENCY
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_Single_CompleteArtifact_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += "$GROUP_ID" % "$CARET"
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += $RESULT_DEPENDENCY
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_Single_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += "$GROUP_ID" % $CARET % "0.0.1"
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += $RESULT_DEPENDENCY
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_Single_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += "$GROUP_ID" % "$CARET" % "0.0.1"
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += $RESULT_DEPENDENCY
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
         |    libraryDependencies ++= Seq($CARET)
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
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
         |    libraryDependencies ++= Seq("$CARET")
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqOneLine_CompleteArtifact_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq("$GROUP_ID" % $CARET)
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqOneLine_CompleteArtifact_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq("$GROUP_ID" % "$CARET")
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqOneLine_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq("$GROUP_ID" % $CARET % "0.0.1")
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqOneLine_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq("$GROUP_ID" % "$CARET" % "0.0.1")
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq($RESULT_DEPENDENCY)
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
         |    libraryDependencies ++= Seq(
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
         |    libraryDependencies ++= Seq(
         |      $RESULT_DEPENDENCY
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
         |    libraryDependencies ++= Seq(
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
         |    libraryDependencies ++= Seq(
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineFirst_CompleteArtifact_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "$GROUP_ID" % $CARET
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineFirst_CompleteArtifact_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "$GROUP_ID" % "$CARET"
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "$GROUP_ID" % $CARET % "0.0.1"
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "$GROUP_ID" % "$CARET" % "0.0.1"
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      $RESULT_DEPENDENCY
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
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
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
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      $RESULT_DEPENDENCY
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
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
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
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineSecond_CompleteArtifact_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      "$GROUP_ID" % $CARET
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineSecond_CompleteArtifact_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      "$GROUP_ID" % "$CARET"
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      "$GROUP_ID" % $CARET % "0.0.1"
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  def testInProjectSettings_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doCompletionTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      "$GROUP_ID" % "$CARET" % "0.0.1"
         |    )
         |  )
         |""".stripMargin,
    resultText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      $RESULT_DEPENDENCY
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

}
