package org.jetbrains.plugins.scala.testingSupport.test

import java.util.concurrent.TimeUnit

import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.icons.AllIcons.RunConfigurations.TestState
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testIntegration.TestRunLineMarkerProvider
import com.intellij.util.Function
import javax.swing.Icon
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.{inReadAction, _}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.macroAnnotations.Measure
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestRunLineMarkerProvider._
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestLocationsFinder.TestLocations
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.{ScalaTestTestFramework, ScalaTestTestLocationsFinder}
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2TestFramework
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestTestFramework

import scala.annotation.tailrec

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
      case leaf: LeafPsiElement if leaf.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        infoForLeafElement(leaf).orNull
      case _ =>
        null
    }

  // REMINDER from codeInsight.daemon.LineMarkerInfo:
  // LineMarker is supposed to be registered for leaf elements only!
  @Measure
  private def infoForLeafElement(leaf: LeafPsiElement): Option[RunLineMarkerContributor.Info] = {
    val parent = leaf.getParent
    parent match {
      case clazz: PsiClass            => infoForClass(clazz)
      case method: PsiMethod          => infoForMethod(method)
      case ref: ScReferenceExpression => infoForElement(leaf, ref)
      case _                          => None
    }
  }

  @Measure
  private def infoForClass(clazz: PsiClass): Option[RunLineMarkerContributor.Info] = {
    val framework = TestFrameworks.detectFramework(clazz)
    if (framework == null || !framework.isTestClass(clazz)) return None

    val url = framework match {
      case _: ScalaTestTestFramework => Some(s"scalatest://TopOfClass:${clazz.qualifiedName}TestName:${clazz.name}")
      //FIXME: why the hell uTest url is reported with scalatest prefix? (see UTestReporter)
      case _: UTestTestFramework     => Some(s"scalatest://TopOfClass:${clazz.qualifiedName}TestName:${clazz.qualifiedName}") // TODO: check maybe it should be clazz.name
      case _: Specs2TestFramework    => Some(s"") // TODO: spec2 runner does not report location for class currently
      case _                         => Some(s"java:suite://${clazz.qualifiedName}")
    }
    url.map(buildLineInfo(_, clazz.getProject, isClass = true))
  }

  // REMINDER: we shouldn't do any long resolve or some other heavy computation,
  // because we can't block UI thread, we already have a lag in a context menu on a single method (right click),
  // not talking about dozens of methods which should be resolved here
  @Measure
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

  @Measure
  private def infoForElement(leaf: PsiElement, parent: PsiElement): Option[RunLineMarkerContributor.Info] = {
    val definition = PsiTreeUtil.getParentOfType(parent, classOf[ScTypeDefinition])
    if (definition == null) return None

    TestFrameworks.detectFramework(definition) match {
      case _: ScalaTestTestFramework =>
      case _ => return None
    }

    /*
     * - if a single file contains several test classes then recalculation will re-run on sibling classes modifications
     * cause `fileModCount` is used to track changes
     * - if test locations calculation is in process then no other/parallel calculation for the same class will be run
     * - if the class was modified during calculation then after calculation is finished CodeAnalyzer will restart
     * highlighting pass and calculation will be restarted because modification count will be increased
     * - there is no way to cancel the started calculation right now
     */
    val calculationState = definition.getUserData(TestPositionsCalculationStateKey)
    val testLocations: Option[TestLocations] = calculationState match {
      case null =>
        calculateTestLocationsInBackgroundAndRestart(definition, None)
        None
      case Calculating(prevResult) =>
        prevResult
      case Calculated(result, modCountCached, timestamp)  =>
        val modCountCurrent = CachesUtil.fileModCount(definition.getContainingFile)
        if (modCountCached != modCountCurrent) {
          //println(s"### recalculating test locations ($modCountCached -> $modCountCurrent) !")
          calculateTestLocationsInBackgroundAndRestart(definition, result, timestamp)
        }
        result
    }

    testLocations match {
      case Some(locations) if locations.contains(parent) =>
        Some(scalaTestLineInfo(parent, definition, testName = ""))
      case _ =>
        None
    }
  }

  private def calculateTestLocationsInBackgroundAndRestart(
    definition: ScTypeDefinition,
    prevResult: Option[Seq[PsiElement]],
    prevResultsTimestamp: Long = 0L
  ): Unit = {
    val project = definition.getProject
    definition.putUserData(TestPositionsCalculationStateKey, Calculating(prevResult))

    debounce(prevResultsTimestamp, Math.max(0, TestPositionsCalculationDebounceMs)) {
      DumbService.getInstance(project).runWhenSmart { () =>
        executeOnPooledThread {
          inReadAction {
            doCalculateTestLocationsAndRestart(definition)
          }
        }
      }
    }
  }

  private def debounce(prevResultsTimestamp: Long, debounceTime: Long)(body: => Unit): Unit = {
    val currentTime             = System.currentTimeMillis()
    val timePassed              = currentTime - prevResultsTimestamp
    val timeUntilNextInvocation = Math.max(debounceTime - timePassed, 0)
    if (timeUntilNextInvocation > 0) {
      //println(s"### debounced $timeUntilNextInvocation")
      scheduleOnPooledThread(timeUntilNextInvocation, TimeUnit.MILLISECONDS)(body)
    } else {
      body
    }
  }

  private def doCalculateTestLocationsAndRestart(definition: ScTypeDefinition): Unit = try {
    val modCount = CachesUtil.fileModCount(definition.getContainingFile)
    val testLocations: Option[Seq[PsiElement]] =
      for {
        module    <- definition.module
        locations <- ScalaTestTestLocationsFinder.calculateTestLocations(definition, module)
      } yield locations

    definition.putUserData(TestPositionsCalculationStateKey, Calculated(testLocations, modCount, System.currentTimeMillis()))

    val file    = definition.getContainingFile
    val project = definition.getProject
    if (file != null && !project.isDisposed) {
      // TODO: can we restart only a single highlighting pass (LineMarkersPass or even only TestRunLineMarkerProvider)?
      // TODO: can we restart only for a single class?
      DaemonCodeAnalyzer.getInstance(project).restart(file)
    }
  } catch {
    case ex: Throwable =>
      definition.putUserData(TestPositionsCalculationStateKey, null)
      throw ex
  }


  private def scalaTestLineInfo(element: PsiElement, definition: ScTypeDefinition, testName: String): RunLineMarkerContributor.Info = {
    val url = scalaTestLineUrl(element, definition, testName)
    val project = element.getProject
    buildLineInfo(url, project, isClass = false)
  }

  private def scalaTestLineUrl(element: PsiElement, definition: ScTypeDefinition, testName: String): String = {
    // NOTE: there are difficulties with identifying test by test lines due to when test file is modified lines
    // can shift and some url which used to refer to some failing test can suddenly refer to some new test and
    // a failing icon will be shown even if the test is successfully passed

    //val className  = definition.qualifiedName
    //val file       = definition.getContainingFile
    //val fileName   = file.getName
    //val document   = PsiDocumentManager.getInstance(project).getDocument(file)
    //val lineNumber = document.getLineNumber(element.startOffset) + 1
    //s"scalatest://LineInFile:$className:$fileName:${lineNumber}TestName:$testName"

    ""
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

private object ScalaTestRunLineMarkerProvider {

  private val TooltipProvider: Function[PsiElement, String] = (_: PsiElement) => "Run Test"

  private val TestPositionsCalculationStateKey = new Key[TestLocationsCalculationState]("TestPositionsCalculationState")
  private val TestPositionsCalculationDebounceMs: Long = 2000L

  private sealed trait TestLocationsCalculationState
  // prev results is saved just to show at least old gutters if test locations are being recalculated
  private case class Calculating(prevResult: Option[TestLocations]) extends TestLocationsCalculationState
  private case class Calculated(result: Option[TestLocations], classModCount: Long, timestamp: Long) extends TestLocationsCalculationState

  implicit final class PsiElementExt2(private val element: PsiElement) extends AnyVal {

    @tailrec
    @Nullable
    def getParentN(depth: Int): PsiElement = {
      require(depth >= 0)
      if (depth == 0) element else {
        val parent = element.getParent
        if (parent == null) null
        else if (depth == 1) parent
        else parent.getParentN(depth - 1)
      }
    }
  }
}
