package org.jetbrains.sbt.annotator

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.sbt.language.SbtFileImpl

abstract class SbtAnnotatorConformanceTestBase extends SbtAnnotatorTestBase {

  // maybe we can contruct a context without loading a file
  private lazy val file: SbtFileImpl = loadTestFile()
  private lazy implicit val project: Project = file.getProject
  private lazy implicit val typeSystem: TypeSystem = project.typeSystem

  override protected def setUp(): Unit = {
    super.setUp()
    setSbtVersion(sbtVersion)
  }

  lazy val singleSetting: ScExpression = code"""name := "someName"""".asInstanceOf[ScExpression]
  lazy val seqSettings: ScExpression = code"""Seq(organization := "org", scalaVersion := "2.11.8")""".asInstanceOf[ScExpression]

  def typeAllowed(expression: ScExpression, expectedTypeName: String): Boolean =
    SbtAnnotator.isTypeAllowed(expression, expression.getType(TypingContext.empty).get, Seq(expectedTypeName))

  def doConformanceTest(expression: ScExpression, typeNameExpected: String): Unit = {
    val file = loadTestFile() // just for the context. we can probably create a context without loading the file?
    implicit val project = file.getProject
    implicit val typeSystem = project.typeSystem

    expression.setContext(file, null)
    assert(typeAllowed(expression, typeNameExpected), s"$expression should conform to $typeNameExpected")
  }
}

class SbtAnnotatorConformanceTest_0_12_4 extends SbtAnnotatorConformanceTestBase {
  override def sbtVersion = "0.12.4"

  def testSingleSetting(): Unit = doConformanceTest(singleSetting, "Project.Setting[_]")
  def testSeqSettings(): Unit = doConformanceTest(seqSettings, "Seq[Project.Setting[_]]")
}

class SbtAnnotatorConformanceTest_0_13_1 extends SbtAnnotatorConformanceTestBase {
  override def sbtVersion = "0.13.1"

  def testSingleSetting(): Unit = doConformanceTest(singleSetting, "Def.SettingsDefinition")
  def testSeqSettings(): Unit = doConformanceTest(seqSettings, "Seq[Def.SettingsDefinition]")
}

class SbtAnnotatorConformanceTest_0_13_7 extends SbtAnnotatorConformanceTestBase {
  override def sbtVersion = "0.13.7"

  def testSingleSetting(): Unit = doConformanceTest(singleSetting, "sbt.internals.DslEntry")
  def testSeqSettings(): Unit = doConformanceTest(seqSettings, "sbt.internals.DslEntry")
}
