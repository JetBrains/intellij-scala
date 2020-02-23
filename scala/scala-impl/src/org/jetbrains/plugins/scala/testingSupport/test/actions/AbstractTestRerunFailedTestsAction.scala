package org.jetbrains.plugins.scala.testingSupport.test.actions

import java.util

import com.intellij.execution.Executor
import com.intellij.execution.configurations.{RunConfigurationBase, RunProfileState}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.execution.testframework.{AbstractTestProxy, TestConsoleProperties}
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.testingSupport.locationProvider.PsiLocationWithName
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.{PropertiesExtension, TestCommandLinePatcher}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.12.11
 */

class AbstractTestRerunFailedTestsAction(consoleView: ConsoleView)
  extends AbstractRerunFailedTestsActionAdapter(consoleView) {
  ActionUtil.copyFrom(this, "RerunFailedTests")
  registerCustomShortcutSet(getShortcutSet, consoleView.getComponent)

  override def getRunProfile: MyRunProfileAdapter = {
    val properties: TestConsoleProperties = getModel.getProperties
    val configuration = properties.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    new MyRunProfileAdapter(configuration) {
      override def getModules: Array[Module] = configuration.getModules

      def getTestName(failed: AbstractTestProxy): String = {
        failed.getLocation(getProject, GlobalSearchScope.allScope(getProject)) match {
          case PsiLocationWithName(_, _, testName) => testName
          case _ => failed.getName
        }
      }

      override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
        val extensionConfiguration =
          properties.asInstanceOf[PropertiesExtension].getRunConfigurationBase
        val state = configuration.getState(executor, env)
        val patcher = state.asInstanceOf[TestCommandLinePatcher]
        val failedTests = getFailedTests(configuration.getProject).asScala
        val buffer = new ArrayBuffer[(String, String)]
        val classNames = patcher.getClasses.map(s => {
          val i = s.lastIndexOf(".")
          if (i < 0) s
          else s.substring(i + 1)
        } -> s).toMap

        for (failed <- failedTests) { //todo: fix after adding location API
        def tail(): Unit = {
          var parent = failed.getParent
          while (parent != null) {
            classNames.get(parent.getName) match {
              case None =>
                parent = parent.getParent
                if (parent == null) buffer += ((classNames.values.iterator.next(), getTestName(failed)))
              case Some(s) =>
                buffer += ((s, getTestName(failed)))
                parent = null
            }
          }
        }
          if (extensionConfiguration != this && extensionConfiguration.isInstanceOf[MyRunProfileAdapter] &&
            extensionConfiguration.asInstanceOf[MyRunProfileAdapter].previoslyFailed != null) {
            var added = false
            for (f <- extensionConfiguration.asInstanceOf[MyRunProfileAdapter].previoslyFailed if !added) {
              if (f._2 == getTestName(failed)) {
                buffer += f
                added = true
              }
            }
            if (!added) tail()
          } else {
            tail()
          }
        }
        previoslyFailed = buffer
        patcher.setFailedTests(buffer)
        // FIXME workaround cast because superclass MyRunProfile extends RunConfigurationBase without type parameters
        val runConfigHack: RunConfigurationBase[_] = this.asInstanceOf[RunConfigurationBase[_]]
        patcher.setConfiguration(runConfigHack)
        state
      }
    }
  }

  private def isFailed(test: AbstractTestProxy): Boolean = {
    if (!test.isLeaf) return false
    test match {
      case test: SMTestProxy =>
        val info = test.getMagnitudeInfo
        info == Magnitude.FAILED_INDEX || info == Magnitude.ERROR_INDEX
      case _ => !test.isPassed
    }
  }

  override def getFailedTests(project: Project): util.List[AbstractTestProxy] = {
    val list = new util.ArrayList[AbstractTestProxy]()
    val allTests = getModel.getRoot.getAllTests
    allTests.forEach { test =>
      if (isFailed(test)) list.add(test)
    }
    list
  }
}