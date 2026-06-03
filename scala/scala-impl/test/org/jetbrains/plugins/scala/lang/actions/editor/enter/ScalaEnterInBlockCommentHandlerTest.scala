package org.jetbrains.plugins.scala.lang.actions.editor.enter

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

sealed abstract class ScalaEnterInBlockCommentHandlerTestBase(scalaVersion: ScalaVersion) extends EditorActionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.languageLevel == scalaVersion.languageLevel

  def testEmptyFile(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |/*$CARET
       |""".stripMargin,
    s"""
       |/*
       |$CARET
       | */
       |""".stripMargin
  )

  def testBeforeClass(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |/*$CARET
       |class Foo
       |""".stripMargin,
    s"""
       |/*
       |$CARET
       | */
       |class Foo
       |""".stripMargin
  )

  def testInsideClass(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |class Foo {
       |  /*$CARET
       |}
       |""".stripMargin,
    s"""
       |class Foo {
       |  /*
       |  $CARET
       |   */
       |}
       |""".stripMargin
  )

  def testAfterClass(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |class Foo
       |/*$CARET
       |""".stripMargin,
    s"""
       |class Foo
       |/*
       |$CARET
       | */
       |""".stripMargin
  )

  def testInFunction(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |class Foo {
       |  def bar: Int =
       |    /*$CARET
       |}
       |""".stripMargin,
    s"""
       |class Foo {
       |  def bar: Int =
       |    /*
       |    $CARET
       |     */
       |}
       |""".stripMargin
  )
}

final class ScalaEnterInBlockCommentHandlerTest_2_13 extends ScalaEnterInBlockCommentHandlerTestBase(LatestScalaVersions.Scala_2_13)

final class ScalaEnterInBlockCommentHandlerTest_3 extends ScalaEnterInBlockCommentHandlerTestBase(LatestScalaVersions.Scala_3)
