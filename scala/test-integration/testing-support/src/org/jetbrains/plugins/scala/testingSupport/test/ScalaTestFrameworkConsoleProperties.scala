package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.testingSupport.locationProvider.ScalaTestLocationProvider
import org.jetbrains.plugins.scala.testingSupport.test.actions.ScalaRerunFailedTestsAction

import javax.swing.JComponent

class ScalaTestFrameworkConsoleProperties(
  configuration: AbstractTestRunConfiguration,
  testFrameworkName: String,
  executor: Executor
) extends SMTRunnerConsoleProperties(
  configuration,
  testFrameworkName,
  executor
) {

  override def getConfiguration: AbstractTestRunConfiguration =
    super.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]

  override def getTestLocator =
    new ScalaTestLocationProvider

  override def createRerunFailedTestsAction(consoleView: ConsoleView): ScalaRerunFailedTestsAction =
    new ScalaRerunFailedTestsAction(consoleView, this)

  override protected def initScope: GlobalSearchScope =
    super.initScope()

  /**
   * Not 100% sure what's this about, just copied from [[com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties]]
   */
  override def appendAdditionalActions(
    actionGroup: DefaultActionGroup,
    parent: JComponent,
    target: TestConsoleProperties
  ): Unit = {
    super.appendAdditionalActions(actionGroup, parent, target)
    actionGroup.add(createIncludeNonStartedInRerun(target))
  }

  /**
   * Scala test frameworks don't support "Repeat" settings for now (like in junit)
   * (see [[com.intellij.rt.execution.junit.RepeatCount]] )
   */
  override def isUndefined: Boolean = false
}
