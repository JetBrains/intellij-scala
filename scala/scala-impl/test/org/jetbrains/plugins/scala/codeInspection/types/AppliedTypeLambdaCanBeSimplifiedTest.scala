package org.jetbrains.plugins.scala.codeInspection.types

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections.AppliedTypeLambdaCanBeSimplifiedInspection
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

abstract class AppliedTypeLambdaCanBeSimplifiedTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[AppliedTypeLambdaCanBeSimplifiedInspection]

  override protected val description: String = ScalaInspectionBundle.message("applied.type.lambda.can.be.simplified")

  private val hint: String = ScalaInspectionBundle.message("simplify.type")

  protected def testFix(text: String, res: String): Unit = testQuickFix(text, res, hint)

  def testSimple(): Unit = {
    val text = s"def a: $START({type l[a] = Either[String, a]})#l[Int]$END)"
    checkTextHasError(text)
    val code = "def a: ({type l[a] = Either[String, a]})#l[Int]"
    val res = "def a: Either[String, Int]"
    testFix(code, res)
  }
}

class AppliedTypeLambdaCanBeSimplifiedTest extends AppliedTypeLambdaCanBeSimplifiedTestBase {

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "kind-projector"
    )
    defaultProfile.setSettings(newSettings)
  }

  def testKindProjectorFunctionSyntax(): Unit = {
    val text = s"def a: ${START}Lambda[A => (A, A)][Int]$END"
    checkTextHasError(text)
    val code = "def a: Lambda[A => (A, A)][Int]"
    val res = "def a: (Int, Int)"
    testFix(code, res)
  }

  def testKindProjectorInlineSyntax(): Unit = {
    val text = s"def a: ${START}Either[+?, -?][String, Int]$END"
    checkTextHasError(text)
    val code = "def a: Either[+?, -?][String, Int]"
    val res = "def a: Either[String, Int]"
    testFix(code, res)
  }
}

class AppliedTypeLambdaCanBeSimplifiedTest_Without_KindProjector extends AppliedTypeLambdaCanBeSimplifiedTestBase