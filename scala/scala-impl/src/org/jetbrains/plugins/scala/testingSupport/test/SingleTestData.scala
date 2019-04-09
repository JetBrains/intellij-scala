package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RuntimeConfigurationException
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind

import scala.beans.BeanProperty

class SingleTestData(config: AbstractTestRunConfiguration) extends ClassTestData(config) {

  @BeanProperty var testName = ""

  private def splitTests: Array[String] = testName.split("\n").filter(!_.isEmpty)

  override def checkSuiteAndTestName(): Unit = {
    super.checkSuiteAndTestName()
    if (testName == null) throw new RuntimeConfigurationException("Test Name is not specified")
  }

  override def getTestMap: Map[String, Set[String]] = {
    if (isDumb) return Map(testClassPath -> Set(testName))
    val clazzMap = super.getTestMap
    if (clazzMap.size != 1) throw new ExecutionException("Multiple classes specified for single-test run")
    clazzMap.map{ case (aClazz, _) => (aClazz, splitTests.toSet)}
  }

  override def getKind: TestKind = TestKind.TEST_NAME

  override def apply(form: TestRunConfigurationForm): Unit = {
    super.apply(form)
    testName = form.getTestName
  }
}
