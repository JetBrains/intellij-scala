package org.jetbrains.plugins.scala.testingSupport.test

import java.util.{ArrayList, List}

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.testframework.{AbstractTestProxy, TestConsoleProperties}
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.testingSupport.locationProvider.PsiLocationWithName
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.{PropertiesExtension, TestCommandLinePatcher}

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.12.11
 */

class AbstractTestRerunFailedTestsAction(consoleView: BaseTestsOutputConsoleView)
  extends AbstractRerunFailedTestsActionAdapter(consoleView) {
  copyFrom(ActionManager.getInstance.getAction("RerunFailedTests"))
  registerCustomShortcutSet(getShortcutSet, consoleView.getComponent)

  override def getRunProfile: MyRunProfileAdapter = {
    val properties: TestConsoleProperties = getModel.getProperties
    val configuration = properties.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    new MyRunProfileAdapter(configuration) {
      def getModules: Array[Module] = configuration.getModules

      def getTestName(failed: AbstractTestProxy): String = {
        failed.getLocation(getProject, GlobalSearchScope.allScope(getProject)) match {
          case PsiLocationWithName(_, _, testName) => testName
          case _ => failed.getName
        }
      }

      def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
        val extensionConfiguration =
          properties.asInstanceOf[PropertiesExtension].getRunConfigurationBase
        val state = configuration.getState(executor, env)
        val patcher = state.asInstanceOf[TestCommandLinePatcher]
        val failedTests = getFailedTests(configuration.getProject)
        val buffer = new ArrayBuffer[(String, String)]
        val classNames = patcher.getClasses.map(s => {
          val i = s.lastIndexOf(".")
          if (i < 0) s
          else s.substring(i + 1)
        } -> s).toMap
        import scala.collection.JavaConversions._
        for (failed <- failedTests) { //todo: fix after adding location API
        def tail() {
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
        patcher.setConfiguration(this)
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

  override def getFailedTests(project: Project): List[AbstractTestProxy] = {
    val list = new ArrayList[AbstractTestProxy]()
    val allTests = getModel.getRoot.getAllTests
    import scala.collection.JavaConversions._
    for (test <- allTests if isFailed(test)) list add test
    list
  }
}