package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.codeInsight.TestFrameworks
import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testIntegration.{TestFramework, TestRunLineMarkerProvider}
import com.intellij.util.Function
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2TestFramework
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestTestFramework

/**
  * Created by Roman.Shein on 02.11.2016.
  * <p/>
  * Mostly copy-paste from [[com.intellij.testIntegration.TestRunLineMarkerProvider]], inaviodable due to private methods.
  */
class ScalaTestRunLineMarkerProvider extends TestRunLineMarkerProvider {
  override def getInfo(e: PsiElement): RunLineMarkerContributor.Info = {
    if (isIdentifier(e)) {
      val element: PsiElement = e.getParent
      element match {
        case cl: PsiClass =>
          val framework: TestFramework = TestFrameworks.detectFramework(cl)
          if (framework != null && framework.isTestClass(cl)) {
            val url = framework match {
              case _: UTestTestFramework | _: ScalaTestTestFramework | _: Specs2TestFramework =>
                //TODO do nothing for now; gutter icons for classes require proper url processing for each framework
                return null
              case _ =>
                "java:suite://" + cl.getQualifiedName
            }
            return getInfo(url, e.getProject, isClass = true)
          }
        case _: PsiMethod =>
          val psiClass: PsiClass = PsiTreeUtil.getParentOfType(element, classOf[PsiClass])
          if (psiClass != null) {
            val framework: TestFramework = TestFrameworks.detectFramework(psiClass)
            if (framework != null && framework.isTestMethod(element)) {
              val url = framework match {
                case _: UTestTestFramework | _: ScalaTestTestFramework | _: Specs2TestFramework =>
                  //TODO do nothing for now; gutter icons for methods require proper url processing for each framework
                  return null
                case _ =>
                  "java:test://" + psiClass.getQualifiedName + "." + element.asInstanceOf[PsiMethod].getName
              }
              return getInfo(url, e.getProject, isClass = false)
            }
          }
        case _ =>
      }
    }
    null
  }

  override def isIdentifier(e: PsiElement): Boolean = e match {
    case l: LeafPsiElement => l.getElementType == ScalaTokenTypes.tIDENTIFIER
    case _ => false
  }

  private def getInfo(url: String, project: Project, isClass: Boolean) = {
    import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude._
    Option(TestStateStorage.getInstance(project).getState(url)).
      map(state => TestIconMapper.getMagnitude(state.magnitude)).map {
      case ERROR_INDEX | FAILED_INDEX => AllIcons.RunConfigurations.TestState.Red2
      case PASSED_INDEX | COMPLETE_INDEX => AllIcons.RunConfigurations.TestState.Green2
      case _ => if (isClass) AllIcons.RunConfigurations.TestState.Run_run else AllIcons.RunConfigurations.TestState.Run
    }.map {
      icon =>
        new RunLineMarkerContributor.Info(icon, ScalaTestRunLineMarkerProvider.TOOLTIP_PROVIDER,
          ExecutorAction.getActions(1): _*)
    }.orNull
  }
}

object ScalaTestRunLineMarkerProvider {
  val TOOLTIP_PROVIDER = new Function[PsiElement, String] {
    override def fun(param: PsiElement): String = "Run Test"
  }
}
