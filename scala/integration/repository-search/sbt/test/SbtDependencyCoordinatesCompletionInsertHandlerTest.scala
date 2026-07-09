package org.jetbrains.plugins.scala.reposearch.sbt

import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.sbt.lang.completion.SbtDependencyCompletionInsertHandlerTestBase
import org.junit.Test

//noinspection ApiStatus
final class SbtDependencyCoordinatesCompletionInsertHandlerTest extends SbtDependencyCompletionInsertHandlerTestBase {

  @Test
  def testTopLevel_CompletionDoesNotStopOutsideStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies += ("$GROUP_ID" %% "$ARTIFACT_ID" % "$STABLE_VERSION").in$CARET
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += ("$GROUP_ID" %% "$ARTIFACT_ID" % "$STABLE_VERSION").intransitive()$CARET
         |""".stripMargin,
    item = "intransitive"
  )

  @Test
  def testTopLevel_Single_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies += org$CARET
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += $RESULT_DEPENDENCY
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_Single_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies += "org$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += $RESULT_DEPENDENCY
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_Single_InsideOfStringLiteral_TooShortPrefix_NoCompletion(): Unit = {
    setupCaches()
    checkNoBasicCompletion(
      fileText =
        s"""
           |libraryDependencies += "$CARET"
           |""".stripMargin,
      item = LOOKUP_ITEM
    )
  }

  @Test
  def testTopLevel_Single_InsideOfStringLiteral_EmptyPrefix_NoCompletionEvenOnExplicitCall(): Unit = {
    setupCaches()
    checkNoBasicCompletion(
      fileText =
        s"""
           |libraryDependencies += "o$CARET"
           |""".stripMargin,
      item = LOOKUP_ITEM,
      invocationCount = 1
    )
  }

  @Test
  def testTopLevel_Single_InsideOfStringLiteral_TooShortPrefix_CompletionOnExplicitCall(): Unit = {
    setupCaches()
    doCompletionTest(
      fileText =
        s"""
           |libraryDependencies += "o$CARET"
           |""".stripMargin,
      resultText =
        s"""
           |libraryDependencies += $RESULT_DEPENDENCY
           |""".stripMargin,
      item = LOOKUP_ITEM,
      invocationCount = 1
    )
  }

  @Test
  def testTopLevel_Single_InsideOfStringLiteral_TooShortPrefix_NoCompletion2(): Unit = {
    setupCaches()
    checkNoBasicCompletion(
      fileText =
        s"""
           |libraryDependencies += "or$CARET"
           |""".stripMargin,
      item = LOOKUP_ITEM
    )
  }

  @Test
  def testTopLevel_Single_InsideOfStringLiteral_TooShortPrefix_CompletionOnExplicitCall2(): Unit = {
    setupCaches()
    doCompletionTest(
      fileText =
        s"""
           |libraryDependencies += "or$CARET"
           |""".stripMargin,
      resultText =
        s"""
           |libraryDependencies += $RESULT_DEPENDENCY
           |""".stripMargin,
      item = LOOKUP_ITEM,
      invocationCount = 1
    )
  }

  @Test
  def testTopLevel_Single_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testTopLevel_Single_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testTopLevel_Single_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies += "$GROUP_ID" % $CARET % "0.0.1"
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_Single_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies += "$GROUP_ID" % "$CARET" % "0.0.1"
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies += "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_SeqOneLine_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(org$CARET)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_SeqOneLine_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("org$CARET")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  //region completion inside Seq inheritors
  @Test
  def testTopLevel_ListOneLine_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= List(org$CARET)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= List($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_ListOneLine_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= List("org$CARET")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= List($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_VectorOneLine_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Vector(org$CARET)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Vector($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_VectorOneLine_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Vector("org$CARET")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Vector($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )
  //endregion

  @Test
  def testTopLevel_SeqOneLine_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testTopLevel_SeqOneLine_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testTopLevel_SeqOneLine_CompleteArtifact_InsideOfMultilineStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % ""\"$CARET""\")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq($RESULT_DEPENDENCY)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_SeqOneLine_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % $CARET % "0.0.1")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1")
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_SeqOneLine_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % "$CARET" % "0.0.1")
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq("$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1")
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_SeqMultilineFirst_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  org$CARET
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

  @Test
  def testTopLevel_SeqMultilineFirst_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org$CARET"
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

  @Test
  def testTopLevel_SeqMultilineFirst_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testTopLevel_SeqMultilineFirst_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testTopLevel_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$GROUP_ID" % $CARET % "0.0.1"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$GROUP_ID" % "$CARET" % "0.0.1"
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_SeqMultilineSecond_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  org$CARET
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

  @Test
  def testTopLevel_SeqMultilineSecond_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "foo" % "bar" % "baz",
         |  "org$CARET"
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

  @Test
  def testTopLevel_SeqMultilineSecond_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testTopLevel_SeqMultilineSecond_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testTopLevel_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
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
         |  "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testTopLevel_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
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
         |  "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |)
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testInProjectSettings_Single_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += org$CARET
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

  @Test
  def testInProjectSettings_Single_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies += "org$CARET"
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

  @Test
  def testInProjectSettings_Single_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testInProjectSettings_Single_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testInProjectSettings_Single_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
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
         |    libraryDependencies += "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testInProjectSettings_Single_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
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
         |    libraryDependencies += "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testInProjectSettings_SeqOneLine_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(org$CARET)
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

  @Test
  def testInProjectSettings_SeqOneLine_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq("org$CARET")
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

  @Test
  def testInProjectSettings_SeqOneLine_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testInProjectSettings_SeqOneLine_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testInProjectSettings_SeqOneLine_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
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
         |    libraryDependencies ++= Seq("$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1")
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testInProjectSettings_SeqOneLine_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
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
         |    libraryDependencies ++= Seq("$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1")
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testInProjectSettings_SeqMultilineFirst_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      org$CARET
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

  @Test
  def testInProjectSettings_SeqMultilineFirst_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "org$CARET"
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

  @Test
  def testInProjectSettings_SeqMultilineFirst_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testInProjectSettings_SeqMultilineFirst_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testInProjectSettings_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
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
         |      "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testInProjectSettings_SeqMultilineFirst_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
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
         |      "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testInProjectSettings_SeqMultilineSecond_OutsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      org$CARET
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

  @Test
  def testInProjectSettings_SeqMultilineSecond_InsideOfStringLiteral(): Unit = doTest(
    fileText =
      s"""
         |lazy val foo = project.in(file("foo"))
         |  .settings(
         |    name := "foo",
         |    scalaVersion := "${version.minor}",
         |    libraryDependencies ++= Seq(
         |      "foo" % "bar" % "baz",
         |      "org$CARET"
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

  @Test
  def testInProjectSettings_SeqMultilineSecond_CompleteArtifact_OutsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testInProjectSettings_SeqMultilineSecond_CompleteArtifact_InsideOfStringLiteral(): Unit = doTest(
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

  @Test
  def testInProjectSettings_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_OutsideOfStringLiteral(): Unit = doTest(
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
         |      "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  @Test
  def testInProjectSettings_SeqMultilineSecond_CompleteArtifactWithDefinedVersion_InsideOfStringLiteral(): Unit = doTest(
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
         |      "$GROUP_ID" % "$ARTIFACT_ID$CARET" % "0.0.1"
         |    )
         |  )
         |""".stripMargin,
    item = LOOKUP_ITEM
  )

  //region needs `with MockSbt_1_0`
  @Test
  def testTopLevel_VariableWithType_OutsideOfStringLiteral(): Unit = doTest(
    fileText = s"val dep: ModuleID = org$CARET",
    resultText = s"val dep: ModuleID = $RESULT_DEPENDENCY",
    item = LOOKUP_ITEM
  )

//  @Test
//  def testTopLevel_VariableWithType_WithPrefix_OutsideOfStringLiteral(): Unit = doTest(
//    fileText = s"val dep: ModuleID = org$CARET",
//    resultText = s"val dep: ModuleID = $RESULT_DEPENDENCY",
//    item = LOOKUP_ITEM
//  )

  @Test
  def testTopLevel_SeqWithType_OutsideOfStringLiteral(): Unit = doTest(
    fileText = s"val deps: Seq[ModuleID] = Seq(org$CARET)",
    resultText = s"val deps: Seq[ModuleID] = Seq($RESULT_DEPENDENCY)",
    item = LOOKUP_ITEM
  )

//  @Test
//  def testTopLevel_SeqWithType_WithPrefix_OutsideOfStringLiteral(): Unit = doTest(
//    fileText = s"val deps: Seq[ModuleID] = Seq(org$CARET)",
//    resultText = s"val deps: Seq[ModuleID] = Seq($RESULT_DEPENDENCY)",
//    item = LOOKUP_ITEM
//  )

  // coursier has a limitation that it suggests only via strict prefix matching
  // e.g.: `some.com` can be resolved to `some.company` but `compan` cannot
  // in ScalaLocalDependencyCompletionContributor, we try to work around this
  @Test
  def testTopLevel_VariableWithType_WithGroupIdSuffix_OutsideOfStringLiteral(): Unit = doTest(
    fileText = s"val dep: ModuleID = company$CARET",
    resultText = s"""val dep: ModuleID = "some.company" % "fancy-sdk" % "$CARET"""",
    item = "some.company:fancy-sdk",
    setupCaches = () => {
      DependencyUtil.updateMockGroupIdCompletionCache("some.company")
      DependencyUtil.updateMockArtifactIdCompletionCache("some.company" -> Seq("fancy-sdk"))
    }
  )

  // coursier has a limitation that it suggests only via strict prefix matching
  // e.g.: `some.com` can be resolved to `some.company` but `compan` cannot
  // in ScalaLocalDependencyCompletionContributor, we try to work around this
  @Test
  def testTopLevel_SeqWithType_WithGroupIdSuffix_OutsideOfStringLiteral(): Unit = doTest(
    fileText = s"val deps: Seq[ModuleID] = Seq(company$CARET)",
    resultText = s"""val deps: Seq[ModuleID] = Seq("some.company" % "fancy-sdk" % "$CARET")""",
    item = "some.company:fancy-sdk",
    setupCaches = () => {
      DependencyUtil.updateMockGroupIdCompletionCache("some.company")
      DependencyUtil.updateMockArtifactIdCompletionCache("some.company" -> Seq("fancy-sdk"))
    }
  )

  // coursier has a limitation that it suggests only via strict prefix matching
  // e.g.: `some.company:fa` can be resolved to `some.company:fancy-sdk` but `fancy` cannot
  // in ScalaLocalDependencyCompletionContributor, we try to work around this
  @Test
  def testTopLevel_VariableWithType_WithArtifactIdPrefix_OutsideOfStringLiteral(): Unit = doTest(
    fileText = s"val dep: ModuleID = fancy$CARET",
    resultText = s"""val dep: ModuleID = "some.company" % "fancy-sdk" % "$CARET"""",
    item = "some.company:fancy-sdk",
    setupCaches = () => {
      DependencyUtil.updateMockGroupIdCompletionCache("some.company")
      DependencyUtil.updateMockArtifactIdCompletionCache("some.company" -> Seq("fancy-sdk"))
    }
  )

  // coursier has a limitation that it suggests only via strict prefix matching
  // e.g.: `some.company:fa` can be resolved to `some.company:fancy-sdk` but `fancy` cannot
  // in ScalaLocalDependencyCompletionContributor, we try to work around this
  @Test
  def testTopLevel_SeqWithType_WithArtifactIdPrefix_OutsideOfStringLiteral(): Unit = doTest(
    fileText = s"val deps: Seq[ModuleID] = Seq(fancy$CARET)",
    resultText = s"""val deps: Seq[ModuleID] = Seq("some.company" % "fancy-sdk" % "$CARET")""",
    item = "some.company:fancy-sdk",
    setupCaches = () => {
      DependencyUtil.updateMockGroupIdCompletionCache("some.company")
      DependencyUtil.updateMockArtifactIdCompletionCache("some.company" -> Seq("fancy-sdk"))
    }
  )

  // coursier has a limitation that it suggests only via strict prefix matching
  // e.g.: `some.company:fancy-s` can be resolved to `some.company:fancy-sdk` but `sdk` cannot
  // in ScalaLocalDependencyCompletionContributor, we don't support this case as well
  @Test
  def testTopLevel_VariableWithType_WithArtifactIdSuffix_OutsideOfStringLiteral_NoCompletion(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("some.company")
    DependencyUtil.updateMockArtifactIdCompletionCache("some.company" -> Seq("fancy-sdk"))
    checkNoBasicCompletion(
      fileText = s"val dep: ModuleID = sdk$CARET",
      item = "some.company:fancy-sdk"
    )
  }

  // coursier has a limitation that it suggests only via strict prefix matching
  // e.g.: `some.company:fancy-s` can be resolved to `some.company:fancy-sdk` but `sdk` cannot
  // in ScalaLocalDependencyCompletionContributor, we don't support this case as well
  @Test
  def testTopLevel_SeqWithType_WithArtifactIdSuffix_OutsideOfStringLiteral_NoCompletion(): Unit = {
    DependencyUtil.updateMockGroupIdCompletionCache("some.company")
    DependencyUtil.updateMockArtifactIdCompletionCache("some.company" -> Seq("fancy-sdk"))
    checkNoBasicCompletion(
      fileText = s"val deps: Seq[ModuleID] = Seq(sdk$CARET)",
      item = "some.company:fancy-sdk"
    )
  }
  //endregion

  //region SCL-22717 examples

  private def doTestSCL22717(fileText: String, resultText: String, item: String): Unit =
    doTest(fileText, resultText, item, () => {
      val groupId = GROUP_ID
      val artifactId = "scalatest-app"
      DependencyUtil.updateMockGroupIdCompletionCache(groupId)
      DependencyUtil.updateMockArtifactIdCompletionCache(groupId -> List("2.13", "3").map(versionSuffix => s"${artifactId}_$versionSuffix"))
    })

  // TODO: version tests
  // TODO: in-between artifactId tests -- works fine
  // TODO: groupId tests -- covers `// 2. ref<caret> %% [...] // org` branch!
  // TODO: incomplete definition tests(???)
  @Test
  def testSCL22717_1(): Unit = doTestSCL22717(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-$CARET" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_2(): Unit = doTestSCL22717(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-$CARET" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-app$CARET" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_3(): Unit = doTestSCL22717(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-$CARET" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_4(): Unit = doTestSCL22717(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-$CARET" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_5(): Unit = doTestSCL22717(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-$CARET" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_6(): Unit = doTestSCL22717(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app$CARET" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  ////////
  @Test
  def testSCL22717_1_refOrg(): Unit = doTestSCL22717(
    fileText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-$CARET" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-app$CARET" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_2_refOrg(): Unit = doTestSCL22717(
    fileText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-$CARET" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-app$CARET" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_3_refOrg(): Unit = doTestSCL22717(
    fileText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-$CARET" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-app$CARET" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_4_refOrg(): Unit = doTestSCL22717(
    fileText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-$CARET" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-app$CARET" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_5_refOrg(): Unit = doTestSCL22717(
    fileText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-$CARET" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-app$CARET" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_6_refOrg(): Unit = doTestSCL22717(
    fileText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app$CARET" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |val org = "org.scalatest"
         |
         |libraryDependencies ++= Seq(
         |  org %% "scalatest-" % "3.2.18" % Test,
         |  (org %% "scalatest-" % "3.2.18") % Test,
         |  org %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  (org %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  (((org %% "scalatest-app$CARET" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  ////////

  @Test
  def testSCL22717_2_inOrg(): Unit = doTestSCL22717(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scala${CARET}test" %% "scalatest-" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-app$CARET" % "3.2.18") % Test,
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test) intransitive(),
         |  ("org.scalatest" %% "scalatest-" % "3.2.18" % Test).intransitive(),
         |  ((("org.scalatest" %% "scalatest-app" % "3.2.18" % Test))) intransitive(),
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )

  @Test
  def testSCL22717_2_inArtifactRef(): Unit = doTestSCL22717(
    fileText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% scala${CARET}test)
         |)
         |""".stripMargin,
    resultText =
      s"""
         |libraryDependencies ++= Seq(
         |  "org.scalatest" %% "scalatest-" % "3.2.18" % Test,
         |  ("org.scalatest" %% "scalatest-app" % "$CARET")
         |)
         |""".stripMargin,
    item = "org.scalatest::scalatest-app"
  )
  //endregion
}
