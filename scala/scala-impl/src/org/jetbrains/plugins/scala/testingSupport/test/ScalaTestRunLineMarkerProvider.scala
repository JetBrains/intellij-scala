package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.codeInsight.TestFrameworks
import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.icons.AllIcons.RunConfigurations.TestState
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testIntegration.TestRunLineMarkerProvider
import com.intellij.util.Function
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.macroAnnotations.Measure
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestRunLineMarkerProvider._
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2TestFramework
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestTestFramework

class ScalaTestRunLineMarkerProvider extends TestRunLineMarkerProvider {

  /**
   * for scalatest location hint format see
   * [[org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter]]
   * [[org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporterWithLocation]]
   * [[org.jetbrains.plugins.scala.testingSupport.locationProvider.ScalaTestLocationProvider]]
   *
   * for uTest  location hint format see
   * [[org.jetbrains.plugins.scala.testingSupport.uTest.UTestReporter]]
   */
  @Measure
  override def getInfo(element: PsiElement): RunLineMarkerContributor.Info =
    element match {
      case leaf: LeafPsiElement =>
        import ScalaTokenTypes._
        val elementType = leaf.getElementType
        if (elementType == tIDENTIFIER || STRING_LITERAL_TOKEN_SET.contains(elementType)) {
          infoForLeafElement(leaf).orNull
        } else {
          null
        }
      case _  =>
        null
    }

  // REMINDER from codeInsight.daemon.LineMarkerInfo:
  // LineMarker is supposed to be registered for leaf elements only!
  private def infoForLeafElement(leaf: LeafPsiElement): Option[RunLineMarkerContributor.Info] = {
    val parent = leaf.getParent
    parent match {
      case clazz: PsiClass   => infoForClass(clazz)
      case method: PsiMethod => infoForMethod(method)
      case _                 => None
    }
  }

  private def infoForClass(clazz: PsiClass): Option[RunLineMarkerContributor.Info] = {
    val framework = TestFrameworks.detectFramework(clazz)
    if (framework == null || !framework.isTestClass(clazz)) return None

    val url = framework match {
      case _: ScalaTestTestFramework => Some(s"scalatest://TopOfClass:${clazz.qualifiedName}TestName:${clazz.qualifiedName}")
      //FIXME: why the hell uTest url is reported as scalatest? (see UTestReporter)
      case _: UTestTestFramework     => Some(s"scalatest://TopOfClass:${clazz.qualifiedName}TestName:${clazz.qualifiedName}")
      case _: Specs2TestFramework    => Some(s"") // TODO: spec2 runner does not report location for class currently
      case _                         => Some(s"java:suite://${clazz.qualifiedName}")
    }
    url.map(buildLineInfo(_, clazz.getProject, isClass = true))
  }

  // REMINDER: we shouldn't do any long resolve or some other heavy computation,
  // because we can't block UI thread, we already have a lag in a context menu on a single method (right click),
  // not talking about dozens of methods which should be resolved here
  private def infoForMethod(method: PsiMethod): Option[RunLineMarkerContributor.Info] = {
    val clazz: PsiClass = PsiTreeUtil.getParentOfType(method, classOf[PsiClass])
    if (clazz == null) return None
    val framework = TestFrameworks.detectFramework(clazz)
    if (framework == null || !framework.isTestMethod(method)) return None

    val url = framework match {
      case _: AbstractTestFramework => None
      case _                        => Some(s"java:test://${clazz.qualifiedName}.${method.getName}")
    }
    url.map(buildLineInfo(_, method.getProject, isClass = false))
  }

  protected def buildLineInfo(url: String, project: Project, isClass: Boolean): RunLineMarkerContributor.Info = {
    val icon = iconFor(url, project, isClass)
    val actions = ExecutorAction.getActions(1)
    new RunLineMarkerContributor.Info(icon, TooltipProvider, actions: _*)
  }

  private def iconFor(url: String, project: Project, isClass: Boolean): Icon = {
    import Magnitude._

    def defaultIcon =
      if (isClass) TestState.Run_run
      else TestState.Run

    val testState = Option(TestStateStorage.getInstance(project).getState(url))
    val testMagnitude = testState.map(state => TestIconMapper.getMagnitude(state.magnitude))

    testMagnitude.fold(defaultIcon) {
      case ERROR_INDEX | FAILED_INDEX    => TestState.Red2
      case PASSED_INDEX | COMPLETE_INDEX => TestState.Green2
      case _                             => defaultIcon
    }
  }
}

object ScalaTestRunLineMarkerProvider {

  private val TooltipProvider: Function[PsiElement, String] = (_: PsiElement) => "Run Test"
}
