package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.openapi.util.JDOMExternalizer
import org.jdom.Element
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind

import scala.beans.BeanProperty

class SingleTestData(override val config: AbstractTestRunConfiguration) extends ClassTestData(config) {
  override def checkSuiteAndTestName(): Unit = {
    super.checkSuiteAndTestName()
    if (testName == null) throw new RuntimeConfigurationException("Test Name is not specified")
  }

  @BeanProperty
  var testName = ""
  private def splitTests: Array[String] = testName.split("\n").filter(!_.isEmpty)

  override def getTestMap(): Map[String, Set[String]] = {
    if (isDumb) return Map(testClassPath -> Set(testName))
    val clazzMap = super.getTestMap()
    if (clazzMap.size != 1) throw new ExecutionException("Multiple classes specified for single-test run")
    clazzMap.map{ case (aClazz, _) => (aClazz, splitTests.toSet)}
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    testName = JDOMExternalizer.readString(element, "testName")
  }

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "testName", testName)
  }

  override def getKind: TestKind = TestKind.TEST_NAME

  override def apply(form: TestRunConfigurationForm): Unit = {
    super.apply(form)
    testName = form.getTestName
  }
}
