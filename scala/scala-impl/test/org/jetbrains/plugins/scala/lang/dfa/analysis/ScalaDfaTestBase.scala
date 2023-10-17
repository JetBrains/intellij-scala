package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.InspectionManager
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.dfa.commonCodeTemplate
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
abstract class ScalaDfaTestBase extends ScalaLightCodeInsightFixtureTestCase with AssertionMatchers {

  protected def codeFromMethodBody(returnType: String)(body: String): String = commonCodeTemplate(returnType)(body)

  def testWithUnsupportedPsiElements(code: String)(expectedResult: (String, String)*): Unit =
    test(code, buildUnsupportedPsiElements = true)(expectedResult: _*)

  def test(code: String, buildUnsupportedPsiElements: Boolean = false)(expectedResult: (String, String)*): Unit = {
    val actualFile = configureFromFileText(code)

    val inspectionManager = InspectionManager.getInstance(getProject)
    val mockProblemsHolder = new MockProblemsHolder(actualFile, inspectionManager)
    val dfaVisitor = new ScalaDfaVisitor(mockProblemsHolder, buildUnsupportedPsiElements)

    actualFile.accept(new ScalaRecursiveElementVisitor {
      override def visitScalaElement(element: ScalaPsiElement): Unit = {
        element.accept(dfaVisitor)
        element.acceptChildren(this)
      }
    })

    val actualResult = mockProblemsHolder.collectProblems.map {
      case MockProblemDescriptor(psiElement, message) => psiElement.getText -> message
    }

    actualResult.sorted shouldBe expectedResult.toList.sorted
  }
}
