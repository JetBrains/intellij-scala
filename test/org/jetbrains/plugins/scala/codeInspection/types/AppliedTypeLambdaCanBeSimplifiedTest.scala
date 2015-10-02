package org.jetbrains.plugins.scala.codeInspection.types

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify.AppliedTypeLambdaCanBeSimplifiedInspection
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaLightInspectionFixtureTestAdapter}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/6/15
 */
class AppliedTypeLambdaCanBeSimplifiedTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[AppliedTypeLambdaCanBeSimplifiedInspection]

  def quickFixAnnotation: String = InspectionBundle.message("simplify.type")

  override protected def annotation: String = InspectionBundle.message("applied.type.lambda.can.be.simplified")

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings
    newSettings.plugins :+= "kind-projector" //only some of the tests require kind-projector
    defaultProfile.setSettings(newSettings)
  }

  def testSimple(): Unit = {
    val text = s"def a: $START({type l[a] = Either[String, a]})#l[Int]$END)"
    check(text)
    val code = "def a: ({type l[a] = Either[String, a]})#l[Int]"
    val res = "def a: Either[String, Int]"
    testFix(code, res)
  }

  def testKindProjectorFunctionSyntax(): Unit = {
    val text = s"def a: ${START}Lambda[A => (A, A)][Int]$END"
    check(text)
    val code = "def a: Lambda[A => (A, A)][Int]"
    val res = "def a: (Int, Int)"
    testFix(code, res)
  }

  def testKindProjectorInlineSyntax(): Unit = {
    val text = s"def a: ${START}Either[+?, -?][String, Int]$END"
    check(text)
    val code = "def a: Either[+?, -?][String, Int]"
    val res = "def a: Either[String, Int]"
    testFix(code, res)
  }

  def testFix(text: String, res: String): Unit = testFix(text, res, quickFixAnnotation)

}
