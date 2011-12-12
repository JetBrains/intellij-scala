package org.jetbrains.plugins.scala.testingSupport.scalaTest

import javax.swing.JComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import collection.mutable.ArrayBuffer
import com.intellij.openapi.project.Project
import com.intellij.execution.testframework.AbstractTestProxy
import java.util.{ArrayList, List}
import com.intellij.execution.configurations.{RuntimeConfiguration, RunProfileState}
import org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunConfiguration.{ScalaPropertiesExtension, ScalaTestCommandLinePatcher}
import org.jetbrains.plugins.scala.testingSupport.locationProvider.PsiLocationWithName
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.12.11
 */

class ScalaTestRerunFailedTestsAction(parent: JComponent)
  extends AbstractRerunFailedTestsActionAdapter {
  copyFrom(ActionManager.getInstance.getAction("RerunFailedTests"))
  registerCustomShortcutSet(getShortcutSet, parent)

  override def getRunProfile: MyRunProfileAdapter = {
    val configuration: RuntimeConfiguration = getModel.getProperties.getConfiguration
    new MyRunProfileAdapter(configuration) {
      def getModules: Array[Module] = configuration.getModules

      def getTestName(failed: AbstractTestProxy): String = {
        failed.getLocation(getProject) match {
          case PsiLocationWithName(_, _, testName) => testName
          case _ => failed.getName
        }
      }

      def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
        val extensionConfiguration =
          getModel.getProperties.asInstanceOf[ScalaPropertiesExtension].getRunConfigurationBase
        val state = configuration.getState(executor, env)
        val patcher = state.asInstanceOf[ScalaTestCommandLinePatcher]
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