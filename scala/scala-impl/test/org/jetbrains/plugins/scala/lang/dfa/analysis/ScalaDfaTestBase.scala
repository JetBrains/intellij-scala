package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.InspectionManager
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.dfa.defaultCodeTemplate
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}

abstract class ScalaDfaTestBase extends ScalaLightCodeInsightFixtureTestAdapter with AssertionMatchers {

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(classOf[ScalaDfaVisitor])

  protected def codeFromMethodBody(returnType: String)(body: String): String = defaultCodeTemplate(returnType)(body)

  def test(code: String)(expectedResult: Seq[(String, String)]): Unit = {
    val actualFile = configureFromFileText(code)

    val inspectionManager = InspectionManager.getInstance(getProject)
    val mockProblemsHolder = new MockProblemsHolder(actualFile, inspectionManager)
    val dfaVisitor = new ScalaDfaVisitor(mockProblemsHolder)

    actualFile.accept(new ScalaRecursiveElementVisitor {
      override def visitScalaElement(element: ScalaPsiElement): Unit = {
        element.accept(dfaVisitor)
        element.acceptChildren(this)
      }
    })

    val actualResult = mockProblemsHolder.collectProblems.map {
      case MockProblemDescriptor(psiElement, message) => psiElement.getText -> message
    }

    actualResult.sorted shouldBe expectedResult.sorted
  }
}
