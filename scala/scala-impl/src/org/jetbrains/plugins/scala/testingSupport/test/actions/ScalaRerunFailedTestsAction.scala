package org.jetbrains.plugins.scala.testingSupport.test.actions

import java.util

import org.jetbrains.plugins.scala.extensions.OptionExt
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction.MyRunProfile
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.testingSupport.locationProvider.PsiLocationWithName
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.{PropertiesExtension, TestCommandLinePatcher}

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer

class ScalaRerunFailedTestsAction(consoleView: ConsoleView)
  extends AbstractRerunFailedTestsAction(consoleView) {

  ActionUtil.copyFrom(this, "RerunFailedTests")
  registerCustomShortcutSet(getShortcutSet, consoleView.getComponent)

  override def getFailedTests(project: Project): util.List[AbstractTestProxy] =
    getModel.getRoot.getAllTests.asScala.filter(isFailed).asJava.asInstanceOf[util.List[AbstractTestProxy]]

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
    new MyScalaRunProfile(configuration, properties.asInstanceOf[PropertiesExtension])
  }

  private class MyScalaRunProfile(
    configuration: AbstractTestRunConfiguration,
    propertiesExtension: PropertiesExtension
  ) extends MyRunProfile(configuration) {

    private var previouslyFailed: Option[collection.Seq[Tuple2[String, String]]] = None

    override def getModules: Array[Module] = configuration.getModules

    override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
      val state = configuration.getState(executor, env)

      val buffer = new ArrayBuffer[(String, String)]

      val extensionConfiguration = propertiesExtension.getRunConfigurationBase
      val patcher = state.asInstanceOf[TestCommandLinePatcher]
      val classNames = patcher.getClasses.groupBy(fqnToSimpleName).mapValues(_.head)

      val failedTests = getFailedTests(configuration.getProject).asScala
      for (failedTest <- failedTests) { //todo: fix after adding location API
        def tail(): Unit = {
          var parent = failedTest.getParent
          while (parent != null) {
            classNames.get(parent.getName) match {
              case None =>
                parent = parent.getParent
                if (parent == null)
                  buffer += ((classNames.values.iterator.next(), getTestName(failedTest)))
              case Some(s) =>
                buffer += ((s, getTestName(failedTest)))
                parent = null
            }
          }
        }

        val previouslyFailed = for {
          profile          <- Option(extensionConfiguration).filterByType[MyScalaRunProfile]
          previouslyFailed <- profile.previouslyFailed
          failed           <- previouslyFailed.find(_._2 == getTestName(failedTest))
        } yield failed
        previouslyFailed match {
          case Some(failed) =>
            buffer += failed
          case None         =>
            tail()
        }
      }
      previouslyFailed = Some(buffer)
      patcher.setFailedTests(buffer)
      patcher.setConfiguration(this)

      state
    }

    private def getTestName(failed: AbstractTestProxy): String =
      failed.getLocation(getProject, GlobalSearchScope.allScope(getProject)) match {
        case PsiLocationWithName(_, _, testName) => testName
        case _ => failed.getName
      }
  }

  private def fqnToSimpleName(className: String): String = {
    val i = className.lastIndexOf(".")
    if (i < 0) className
    else className.substring(i + 1)
  }
}