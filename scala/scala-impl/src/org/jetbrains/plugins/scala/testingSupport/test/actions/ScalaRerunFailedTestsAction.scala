package org.jetbrains.plugins.scala.testingSupport.test.actions

import java.util
import java.util.stream.Collectors

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction.MyRunProfile
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.execution.testframework.sm.runner.{SMTRunnerConsoleProperties, SMTestProxy}
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.IteratorExt
import org.jetbrains.plugins.scala.testingSupport.locationProvider.{PsiLocationWithName, ScalaTestLocationProvider}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.actions.ScalaRerunFailedTestsAction.MyScalaRunProfile
import org.jetbrains.plugins.scala.testingSupport.test.munit.MUnitTestLocator

import scala.jdk.CollectionConverters._

@ApiStatus.Internal
class ScalaRerunFailedTestsAction(
  consoleView: ConsoleView,
  properties: SMTRunnerConsoleProperties
) extends AbstractRerunFailedTestsAction(consoleView) {

  locally {
    this.init(properties)
  }

  override def getFailedTests(project: Project): util.List[AbstractTestProxy] = {
    val allTests = getModel.getRoot.getAllTests
    val failedTests = allTests.stream().filter(isFailed)
    val list = failedTests.collect(Collectors.toList[AbstractTestProxy])
    list
  }

  private def isFailed(test: AbstractTestProxy): Boolean =
    if (test.isLeaf)
      test match {
        case test: SMTestProxy => isFailure(test.getMagnitudeInfo)
        case _                 => !test.isPassed
      }
    else
      false

  private def isFailure(info: Magnitude): Boolean =
    info == Magnitude.FAILED_INDEX || info == Magnitude.ERROR_INDEX

  override def getRunProfile(environment: ExecutionEnvironment): MyRunProfile = {
    val properties = getModel.getProperties
    val configuration = properties.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    val failedTestNodes = getFailedTests(configuration.getProject).asScala.toSeq
    new MyScalaRunProfile(configuration, failedTestNodes)
  }
}

object ScalaRerunFailedTestsAction {

  private class MyScalaRunProfile(
    configuration: AbstractTestRunConfiguration,
    failedTestsProxies: Seq[AbstractTestProxy]
  ) extends MyRunProfile(configuration) {

    override def getModules: Array[Module] = configuration.getModules

    override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
      val failedSeq: Seq[(String, String)] = collectFailedTests(failedTestsProxies)
      configuration.runStateProvider.commandLineState(env, Some(failedSeq))
    }

    private def collectFailedTests(failedTestNodes: Seq[AbstractTestProxy]): Seq[(String, String)] =
      for {
        failedTest     <- failedTestNodes
        failedTestName = getFailedTestName(failedTest)
        tailClassFqn   <- detectClassFqn(failedTest)
      } yield (tailClassFqn, failedTestName)

    private def detectClassFqn(failedTest: AbstractTestProxy): Option[String] = {
      val parents = Iterator.iterate(failedTest)(_.getParent).takeWhile(_ != null)
      val parentLocations = parents.map(_.getLocationUrl)
      val classFqns = parentLocations.flatMap { url =>
        detectClassFqnFromUrl(url)
      }
      classFqns.headOption
    }

    private def detectClassFqnFromUrl(url: String): Option[String] =
      if (ScalaTestLocationProvider.isTestUrl(url))
        ScalaTestLocationProvider.getClassFqn(url)
      else if (MUnitTestLocator.isTestUrl(url))
        MUnitTestLocator.getClassFqn(url)
      else
        None

    private def getFailedTestName(failed: AbstractTestProxy): String = {
      val location = failed.getLocation(getProject, GlobalSearchScope.allScope(getProject))
      location match {
        case PsiLocationWithName(_, _, testName) => testName
        case _                                   => failed.getName
      }
    }
  }
}