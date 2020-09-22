package org.jetbrains.plugins.scala.testingSupport.test.actions

import java.util
import java.util.stream.Collectors

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction.MyRunProfile
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.IteratorExt
import org.jetbrains.plugins.scala.testingSupport.locationProvider.PsiLocationWithName
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, ScalaTestFrameworkConsoleProperties}
import org.jetbrains.plugins.scala.testingSupport.test.actions.ScalaRerunFailedTestsAction.MyScalaRunProfile

import scala.jdk.CollectionConverters._

@ApiStatus.Internal
class ScalaRerunFailedTestsAction(consoleView: ConsoleView, properties: ScalaTestFrameworkConsoleProperties)
  extends AbstractRerunFailedTestsAction(consoleView) {

  locally {
    this.init(properties)
  }

  override def getFailedTests(project: Project): util.List[AbstractTestProxy] = {
    val allTests = getModel.getRoot.getAllTests
    val failedTests = allTests.stream().filter(isFailed)
    failedTests.collect(Collectors.toList[AbstractTestProxy])
  }

  private def isFailed(test: AbstractTestProxy): Boolean = {
    if (!test.isLeaf) return false
    test match {
      case test: SMTestProxy =>
        val info = test.getMagnitudeInfo
        info == Magnitude.FAILED_INDEX || info == Magnitude.ERROR_INDEX
      case _ =>
        !test.isPassed
    }
  }

  override def getRunProfile(environment: ExecutionEnvironment): MyRunProfile = {
    val properties = getModel.getProperties
    val configuration = properties.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    new MyScalaRunProfile(
      configuration,
      getFailedTests(configuration.getProject).asScala.toSeq
    )
  }
}

object ScalaRerunFailedTestsAction {

  private class MyScalaRunProfile(
    configuration: AbstractTestRunConfiguration,
    failedTests: Seq[AbstractTestProxy]
  ) extends MyRunProfile(configuration) {

    override def getModules: Array[Module] = configuration.getModules

    override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
      val classes = configuration.testConfigurationData.getTestMap.keys.toSeq
      val failedSeq: Seq[(String, String)] = getFailedTests(classes, failedTests)
      configuration.newCommandLineState(env, Some(failedSeq))
    }

    private def getFailedTests(
      classes: Seq[String],
      failedTests: Seq[AbstractTestProxy]
    ): Seq[(String, String)] = {

      // (TODO: what about same classes in different modules?)
      val classNameToFqn: Map[String, String] =
        classes.groupBy(fqnToSimpleName).view.mapValues(_.head).toMap

      for {
        failedTest <- failedTests
        failedTestName = getFailedTestName(failedTest)
        tailClassFqn <- detectClassFqn(failedTest, classNameToFqn)
      } yield (tailClassFqn, failedTestName)
    }

    private def detectClassFqn(failedTest: AbstractTestProxy, classNames: Map[String, String]): Option[String] = {
      val parents = Iterator.iterate(failedTest.getParent)(_.getParent).takeWhile(_ != null)
      (for {
        parent <- parents
        // can be a class name or intermediate test node name
        // (TODO: what about same classes in different modules?)
        parentName = parent.getName
        classFqn <- classNames.get(parentName)
      } yield classFqn).headOption
    }

    private def getFailedTestName(failed: AbstractTestProxy): String =
      failed.getLocation(getProject, GlobalSearchScope.allScope(getProject)) match {
        case PsiLocationWithName(_, _, testName) => testName
        case _ => failed.getName
      }
  }

  private def fqnToSimpleName(classFqn: String): String = {
    val i = classFqn.lastIndexOf(".")
    if (i < 0) classFqn
    else classFqn.substring(i + 1)
  }
}