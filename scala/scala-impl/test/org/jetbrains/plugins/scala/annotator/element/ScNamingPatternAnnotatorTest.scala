package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.project._

abstract class ScNamingPatternAnnotatorTestBase extends ScalaAnnotatorQuickFixTestBase

class ScNamingPatternAnnotatorTest extends ScNamingPatternAnnotatorTestBase {

  override protected val description = "':' syntax in vararg pattern requires Scala 3.0"

  private val replaceWithAtFix = "Replace with '@'"

  def testVarargPatternWithColonWithBindName(): Unit = {
    val code =
      s"""List(1, 2, 3) match {
         |  case List(first, other$START:$END _*) =>
         |}
         |"""
    checkTextHasError(code)

    testQuickFix(
      code,
      s"""List(1, 2, 3) match {
         |  case List(first, other@_*) =>
         |}
         |""",
      replaceWithAtFix
    )
  }

  def testVarargPatternWithColonWithBindWildcard(): Unit = {
    val code =
      s"""List(1, 2, 3) match {
         |  case List(first, _$START:$END _*) =>
         |}
         |"""
    checkTextHasError(code)

    testQuickFix(
      code,
      s"""List(1, 2, 3) match {
         |  case List(first, _@_*) =>
         |}
         |""",
      replaceWithAtFix
    )
  }

  def testVarargPatternWithAt(): Unit =
    checkTextHasNoErrors(
      s"""List(1, 2, 3) match {
         |  case List(first, other@_*) =>
         |}"""
    )
}

abstract class ScNamingPatternAnnotatorScala3BaseTest extends ScNamingPatternAnnotatorTestBase {

  private val replaceWithColonFix = "Replace with ':'"

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Scala_3_0

  def testVarargPatternWithColonWithBindName(): Unit = {
    val code =
      s"""List(1, 2, 3) match {
         |  case List(first, other$START@$END _*) =>
         |}
         |"""
    checkTextHasError(code)

    testQuickFix(
      code,
      s"""List(1, 2, 3) match {
         |  case List(first, other: _*) =>
         |}
         |""",
      replaceWithColonFix
    )
  }

  def testVarargPatternWithColonWithBindWildcard(): Unit = {
    val code =
      s"""List(1, 2, 3) match {
         |  case List(first, _$START@$END _*) =>
         |}
         |"""
    checkTextHasError(code)

    testQuickFix(
      code,
      s"""List(1, 2, 3) match {
         |  case List(first, _: _*) =>
         |}
         |""",
      replaceWithColonFix
    )
  }

  def testVarargPatternWithColon(): Unit =
    checkTextHasNoErrors(
      s"""List(1, 2, 3) match {
         |  case List(first, other: _*) =>
         |}"""
    )
}

class ScNamingPatternAnnotatorScala3ErrorTest extends ScNamingPatternAnnotatorScala3BaseTest {

  override protected val description = "'@' syntax in vararg pattern has been disabled in Scala 3.0"
}

class ScNamingPatternAnnotatorScala3WarningTest extends ScNamingPatternAnnotatorScala3BaseTest {

  override protected val description = "'@' syntax in vararg pattern has been deprecated since Scala 3.0"

  override def setUpLibraries(implicit module: Module): Unit = {
    super.setUpLibraries
    module.scalaCompilerSettings.scala2Compat = true
  }
}
