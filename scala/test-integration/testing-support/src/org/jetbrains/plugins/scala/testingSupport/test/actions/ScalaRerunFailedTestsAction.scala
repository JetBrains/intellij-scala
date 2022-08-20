package org.jetbrains.plugins.scala.testingSupport.test.actions

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
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.testingSupport.locationProvider.{PsiLocationWithName, ScalaTestLocationProvider}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.actions.ScalaRerunFailedTestsAction.MyScalaRunProfile
import org.jetbrains.plugins.scala.testingSupport.test.munit.MUnitTestLocator
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2RunConfiguration

import java.util
import java.util.stream.Collectors
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
      val isSpec2 = configuration.is[Specs2RunConfiguration]
      if (isSpec2) {
        val classes = configuration.testConfigurationData.getTestMap.keys.toSeq
        val failedSeq = getFailedTestsForSpec2(classes, failedTestsProxies)
        configuration.runStateProvider.commandLineState(env, Some(failedSeq))
      }
      else {
        val failedSeq: Seq[(String, String)] = collectFailedTests(failedTestsProxies)
        configuration.runStateProvider.commandLineState(env, Some(failedSeq))
      }
    }

    private def collectFailedTests(failedTestNodes: Seq[AbstractTestProxy]): Seq[(String, String)] =
      for {
        failedTest     <- failedTestNodes
        failedTestName = getFailedTestName(failedTest)
        tailClassFqn   <- detectClassFqn(failedTest)
      } yield (tailClassFqn, failedTestName)

    private def detectClassFqn(failedTest: AbstractTestProxy): Option[String] = {
      val parents = Iterator.iterate(failedTest)(_.getParent).takeWhile(_ != null).toSeq // TODO: remove toSeq
      val parentLocations = parents.map(_.getLocationUrl).filter(_ != null)
      val classFqns = parentLocations.flatMap(detectClassFqnFromUrl)
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

    /**
     * TODO: this is restored old Hacky solution, it doesn't work for test classes with same name but in different package.
     *  Test location reporting for Spec2 should be rewritten: SCL-8859
     */
    private def getFailedTestsForSpec2(
      classes: Seq[String],
      failedTests: Seq[AbstractTestProxy]
    ): Seq[(String, String)] = {

      // (TODO: what about same classes in different modules?)
      val classNameToFqn: Map[String, String] =
        classes.groupBy(fqnToSimpleName).view.mapValues(_.head).toMap

      for {
        failedTest     <- failedTests
        failedTestName = getFailedTestName(failedTest)
        tailClassFqn   <- detectClassFqnForSpec2(failedTest, classNameToFqn)
      } yield (tailClassFqn, failedTestName)
    }

    private def detectClassFqnForSpec2(failedTest: AbstractTestProxy, classNames: Map[String, String]): Option[String] = {
      val parents = Iterator.iterate(failedTest.getParent)(_.getParent).takeWhile(_ != null)
      (for {
        parent <- parents
        parentName = parent.getName
        classFqn <- classNames.get(parentName)
      } yield classFqn).nextOption()
    }

    private def fqnToSimpleName(classFqn: String): String = {
      val i = classFqn.lastIndexOf(".")
      if (i < 0) classFqn
      else classFqn.substring(i + 1)
    }
  }
}