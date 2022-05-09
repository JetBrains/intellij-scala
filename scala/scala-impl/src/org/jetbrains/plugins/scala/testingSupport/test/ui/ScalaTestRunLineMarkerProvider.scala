package org.jetbrains.plugins.scala.testingSupport.test.ui

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

import javax.swing.Icon
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.macroAnnotations.Measure
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework
import org.jetbrains.plugins.scala.testingSupport.test.munit.{MUnitTestFramework, MUnitTestLocationsFinder, MUnitUtils}
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.{ScalaTestTestFramework, ScalaTestTestLocationsFinder}
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2TestFramework
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestTestFramework

import scala.jdk.CollectionConverters.IterableHasAsScala

// TODO: split providers by test frameworks, if some logic should be reused, just move to some base/utility classes
class ScalaTestRunLineMarkerProvider extends TestRunLineMarkerProvider {

  /**
   * for scalatest location hint format see:
   *  - [[org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter]]
   *  - [[org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporterWithLocation]]
   *  - [[org.jetbrains.plugins.scala.testingSupport.locationProvider.ScalaTestLocationProvider]]
   *
   * for uTest  location hint format see
   *  - [[org.jetbrains.plugins.scala.testingSupport.uTest.UTestReporter]]
   */
  override def getInfo(element: PsiElement): RunLineMarkerContributor.Info =
    element match {
      case leaf: LeafPsiElement if leaf.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        infoForLeafElement(leaf).orNull
      case _                                                                          =>
        null
    }

  // REMINDER from codeInsight.daemon.LineMarkerInfo:
  // LineMarker is supposed to be registered for leaf elements only!
  private def infoForLeafElement(leaf: LeafPsiElement): Option[RunLineMarkerContributor.Info] = {
    val parent = leaf.getParent
    parent match {
      case o: ScObject =>
        infoForScalaTestRefSpec(o) match {
          case info@Some(_) => info
          case _ => infoForClass(o)
        }
      case clazz: PsiClass => infoForClass(clazz)
      case method: PsiMethod => infoForMethod(method)
      case ref: ScReferenceExpression => infoForTestMethodRef(ref)
      case _ => None
    }
  }

  private def infoForClass(clazz: PsiClass): Option[RunLineMarkerContributor.Info] = {
    val framework = TestFrameworks.detectFramework(clazz)
    if (framework == null || !framework.isTestClass(clazz)) return None

    val url = framework match {
      case _: ScalaTestTestFramework =>
        clazz match {
          case _: ScTrait | _: ScObject => None
          case _ => Some(s"scalatest://TopOfClass:${clazz.qualifiedName}TestName:${clazz.name}")
        }
      //FIXME: why the hell uTest url is reported with scalatest prefix? (see UTestReporter)
      case _: UTestTestFramework  => Some(s"scalatest://TopOfClass:${clazz.qualifiedName}TestName:${clazz.qualifiedName}") // TODO: check maybe it should be clazz.name
      case _: Specs2TestFramework => Some(s"") // TODO: spec2 runner does not report location for class currently
      case _                      => Some(s"java:suite://${clazz.qualifiedName}")
    }
    url.map(buildLineInfo(_, clazz.getProject, isClass = true))
  }

  // REMINDER: we shouldn't do any long resolve or some other heavy computation,
  // because we can't block UI thread, we already have a lag in a context menu on a single method (right click),
  // not talking about dozens of methods which should be resolved here
  @Measure
  private def infoForMethod(method: PsiMethod): Option[RunLineMarkerContributor.Info] = {
    val clazz: PsiClass = PsiTreeUtil.getTopmostParentOfType(method, classOf[PsiClass])

    if (clazz == null)
      return None

    val framework = TestFrameworks.detectFramework(clazz)

    if (framework == null)
      return None

    (framework, method) match {
      case (_: ScalaTestTestFramework, f: ScFunctionDefinition) =>
        infoForScalaTestRefSpec(f)
      case _ if framework.isTestMethod(method) =>
        Some(buildLineInfo(
          s"java:test://${clazz.qualifiedName}.${method.getName}",
          method.getProject,
          isClass = false
        ))
      case _ => None
    }
  }

  private def infoForTestMethodRef(ref: ScReferenceExpression): Option[RunLineMarkerContributor.Info] = {
    val definition = PsiTreeUtil.getParentOfType(ref, classOf[ScTypeDefinition])
    if (definition == null)
      return None

    // NOT: for MUnit more than one framework is applicable: MUnit and JUnit, and JUnit is detected first =(
    val frameworks = TestFrameworks.detectApplicableFrameworks(definition).asScala
    val scalaFramework = frameworks.filterByType[AbstractTestFramework].headOption

    scalaFramework.flatMap {
      case _: ScalaTestTestFramework => infoForScalaTestMethodRef(ref, definition)
      case _: MUnitTestFramework     => infoForMUnitMethodRef(ref, definition)
      case _                         => None
    }
  }

  private def infoForScalaTestRefSpec(functionOrObject: ScalaPsiElement): Option[RunLineMarkerContributor.Info] = {
    val definition = PsiTreeUtil.getParentOfType(functionOrObject, classOf[ScClass])

    if (definition == null)
      return None

    val frameworks = TestFrameworks.detectApplicableFrameworks(definition).asScala
    val scalaFramework = frameworks.findByType[ScalaTestTestFramework]

    scalaFramework.flatMap { _ =>
        functionOrObject match {
          case f: ScFunctionDefinition => infoForScalaTestRefSpecFunctionDefinition(f, definition)
          case o: ScObject => infoForScalaTestRefSpecObject(o, definition)
          case _ => None
        }
    }
  }

  private def infoForScalaTestMethodRef(ref: ScReferenceExpression, definition: ScTypeDefinition): Option[RunLineMarkerContributor.Info] = {
    val locations = ScalaTestTestLocationsFinder.calculateTestLocations(definition)
    if (locations.contains(ref)) {
      val url = scalaTestLineUrl(ref, definition, testName = "")
      val info = buildLineInfo(url, ref.getProject, isClass = false)
      Some(info)
    }
    else None
  }

  private def infoForScalaTestRefSpecFunctionDefinition(functionDefinition: ScFunctionDefinition, definition: ScTypeDefinition): Option[RunLineMarkerContributor.Info] = {
    val locations = ScalaTestTestLocationsFinder.calculateTestLocations(definition)
    if (locations.contains(functionDefinition)) {
      val url = scalaTestLineUrl(functionDefinition, definition, testName = "")
      val info = buildLineInfo(url, functionDefinition.getProject, isClass = false)
      Some(info)
    }
    else None
  }

  private def infoForScalaTestRefSpecObject(obj: ScObject, definition: ScTypeDefinition): Option[RunLineMarkerContributor.Info] = {
    val locations = ScalaTestTestLocationsFinder.calculateTestLocations(definition)
    if (locations.contains(obj))
      Some(buildLineInfo("", obj.getProject, isClass = false))
    else None
  }

  private def infoForMUnitMethodRef(ref: ScReferenceExpression, definition: ScTypeDefinition): Option[RunLineMarkerContributor.Info] = {
    val locations = MUnitTestLocationsFinder.calculateTestLocations(definition)
    if (locations.exists(_.contains(ref))) {
      val testName = MUnitUtils.staticTestName(ref)
      val url = testName.fold("")(junit4TestMethodUrl(definition.qualifiedName, _))
      val info = buildLineInfo(url, ref.getProject, isClass = false)
      Some(info)
    }
    else None
  }

  private def junit4TestMethodUrl(classFqn: String, testName: String) =
    s"java:test://$classFqn/$testName"

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
    new RunLineMarkerContributor.Info(icon, (_: PsiElement) => ScalaBundle.message("scalatest.gutter.run.test"), actions: _*)
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