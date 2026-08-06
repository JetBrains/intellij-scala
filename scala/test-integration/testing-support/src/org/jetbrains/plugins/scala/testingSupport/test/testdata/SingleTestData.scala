package org.jetbrains.plugins.scala.testingSupport
package test.testdata

import org.jdom.Element
import org.jetbrains.plugins.scala.testingSupport.test.ui.TestRunConfigurationForm
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestKind}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty

// TODO: it doesn't represent single test already testName can contain multiple tests
class SingleTestData(config: AbstractTestRunConfiguration) extends ClassTestData(config) {

  override def getKind: TestKind = TestKind.TEST_NAME

  @BeanProperty var testName = ""

  private def splitTests: Array[String] = testName.split("\n").filter(!_.isEmpty)

  override def checkSuiteAndTestName: CheckResult =
    for {
      _ <- super.checkSuiteAndTestName
      _ <- check(testName != null, configurationException(TestingSupportBundle.message("test.config.test.name.is.not.specified")))
    } yield ()

  override def getTestMap: Map[String, Set[String]] = {
    if (isDumb) return Map(testClassPath -> Set(testName))
    val clazzMap = super.getTestMap
    if (clazzMap.size != 1) throw executionException(TestingSupportBundle.message("test.config.multiple.classes.specified.for.single.test.run"))
    clazzMap.map{ case (aClazz, _) => (aClazz, splitTests.toSet)}
  }

  override def copyFieldsFromForm(form: TestRunConfigurationForm): Unit = {
    super.copyFieldsFromForm(form)
    testName = form.getTestName
  }

  override protected def copyFieldsFrom(data: ClassTestData): Unit = {
    super.copyFieldsFrom(data)
    data match {
      case d: SingleTestData => testName = d.testName
      case _ =>
    }
  }

  override def copy(config: AbstractTestRunConfiguration): SingleTestData = {
    val data = new SingleTestData(config)
    data.copyFieldsFrom(this)
    data
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    JdomExternalizerMigrationHelper(element) {
      _.migrateString("testName")(testName = _)
    }
  }
}

object SingleTestData {

  def apply(config: AbstractTestRunConfiguration, className: String, testName: String): SingleTestData = {
    val res = new SingleTestData(config)
    res.setTestClassPath(className)
    res.setTestName(testName)
    res
  }
}
