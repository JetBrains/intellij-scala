package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.codeInsight.TestFrameworks
import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.icons.AllIcons.RunConfigurations.TestState
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import com.intellij.testIntegration.{TestFramework, TestRunLineMarkerProvider}
import com.intellij.util.Function
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestRunLineMarkerProvider.TooltipProvider
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2TestFramework
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestTestFramework

/**
  * Created by Roman.Shein on 02.11.2016.
  * <p/>
  * Mostly copy-paste from [[com.intellij.testIntegration.TestRunLineMarkerProvider]], inaviodable due to private methods.
  */
class ScalaTestRunLineMarkerProvider extends TestRunLineMarkerProvider {

  override def getInfo(element: PsiElement): RunLineMarkerContributor.Info =
    if (isIdentifier(element)) {
      getInfoForIdentifier(element)
    } else {
      null
    }

  private def getInfoForIdentifier(element: PsiElement): RunLineMarkerContributor.Info = element.getParent match {
    case clazz: PsiClass   => getInfoForClass(element, clazz).orNull
    case method: PsiMethod => getInfoForMethod(element, method).orNull
    case _                 => null
  }

  private def getInfoForClass(e: PsiElement, clazz: PsiClass): Option[RunLineMarkerContributor.Info] = {
    val framework: TestFramework = TestFrameworks.detectFramework(clazz)
    if (framework == null || !framework.isTestClass(clazz)) return None

    val url = framework match {
      case _: UTestTestFramework | _: Specs2TestFramework => None //TODO do nothing for now; gutter icons for classes require proper url processing for each framework
      case _: ScalaTestTestFramework                      => None // Some(s"scala:suite://${clazz.getQualifiedName}")
      case _                                              => Some(s"java:suite://${clazz.getQualifiedName}")
    }
    url.map(getInfo(_, e.getProject, isClass = true))
  }

  // NOTE: we shouldn't do any long resolve, because we can't block UI thread
  // we even have a lag in a context menu on a single method (right click),
  // not talking about dozens of methods which should be resolved here
  private def getInfoForMethod(e: PsiElement, method: PsiMethod): Option[RunLineMarkerContributor.Info] = {
    val clazz: PsiClass = PsiTreeUtil.getParentOfType(method, classOf[PsiClass])
    if (clazz == null) return None
    val framework: TestFramework = TestFrameworks.detectFramework(clazz)
    if (framework == null || !framework.isTestMethod(method)) return None

    val url = framework match {
      case _: UTestTestFramework | _: Specs2TestFramework => None //TODO do nothing for now; gutter icons for methods require proper url processing for each framework
      case _: ScalaTestTestFramework                      => None // "scala:test://" + psiClass.getQualifiedName + "." + method.getName
      case _                                              => Some(s"java:test://${clazz.getQualifiedName}.${method.getName}")
    }
    url.map(getInfo(_, e.getProject, isClass = false))
  }

  override def isIdentifier(e: PsiElement): Boolean = e match {
    case l: LeafPsiElement => l.getElementType == ScalaTokenTypes.tIDENTIFIER
    case _                 => false
  }

  protected def getInfo(url: String, project: Project, isClass: Boolean): RunLineMarkerContributor.Info = {
    import Magnitude._

    def defaultIcon =
      if (isClass) TestState.Run_run
      else TestState.Run

    val testState = Option(TestStateStorage.getInstance(project).getState(url))
    val icon = testState
      .map(state => TestIconMapper.getMagnitude(state.magnitude))
      .map {
        case ERROR_INDEX | FAILED_INDEX    => TestState.Red2
        case PASSED_INDEX | COMPLETE_INDEX => TestState.Green2
        case _                             => defaultIcon
      }
      .getOrElse(defaultIcon)

    val actions = ExecutorAction.getActions(1)
    new RunLineMarkerContributor.Info(icon, TooltipProvider, actions: _*)
  }
}

object ScalaTestRunLineMarkerProvider {

  private val TooltipProvider: Function[PsiElement, String] = (_: PsiElement) => "Run Test"
}
