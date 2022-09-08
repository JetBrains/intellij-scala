package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.execution.Executor
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.{SMTRunnerConsoleProperties, SMTestLocator}
import com.intellij.execution.ui.ConsoleView
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.testingSupport.test.actions.ScalaRerunFailedTestsAction

final class MUnitConsoleProperties(configuration: MUnitConfiguration, executor: Executor)
  extends SMTRunnerConsoleProperties(configuration, MUnitTestFramework().getName, executor) {

  override def getTestLocator: SMTestLocator =
    new MUnitTestLocator

  override def initScope(): GlobalSearchScope =
    super.initScope()

  override def createRerunFailedTestsAction(consoleView: ConsoleView): AbstractRerunFailedTestsAction =
    new ScalaRerunFailedTestsAction(consoleView, this)
}



