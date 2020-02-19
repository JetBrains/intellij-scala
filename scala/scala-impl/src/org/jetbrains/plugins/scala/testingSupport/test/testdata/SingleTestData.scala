package org.jetbrains.plugins.scala.testingSupport.test.testdata

import com.intellij.execution.ExecutionException
import org.jdom.Element
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestRunConfigurationForm, testdata}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty

class SingleTestData(config: AbstractTestRunConfiguration) extends ClassTestData(config) {

  override def getKind: TestKind = TestKind.TEST_NAME

  @BeanProperty var testName = ""

  private def splitTests: Array[String] = testName.split("\n").filter(!_.isEmpty)

  override def checkSuiteAndTestName: CheckResult =
    for {
      _ <- super.checkSuiteAndTestName
      _ <- check(testName != null, exception(ScalaBundle.message("test.config.test.name.is.not.specified")))
    } yield ()

  override def getTestMap: Map[String, Set[String]] = {
    if (isDumb) return Map(testClassPath -> Set(testName))
    val clazzMap = super.getTestMap
    if (clazzMap.size != 1) throw executionException(ScalaBundle.message("test.config.multiple.classes.specified.for.single.test.run"))
    clazzMap.map{ case (aClazz, _) => (aClazz, splitTests.toSet)}
  }

  override def apply(form: TestRunConfigurationForm): Unit = {
    super.apply(form)
    testName = form.getTestName
  }

  override protected def apply(data: ClassTestData): Unit = {
    super.apply(data)
    data match {
      case d: SingleTestData => testName = d.testName
      case _ =>
    }
  }

  override def copy(config: AbstractTestRunConfiguration): SingleTestData = {
    val data = new SingleTestData(config)
    data.apply(this)
    data
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    JdomExternalizerMigrationHelper(element) {
      _.migrateString("testName")(testName = _)
    }
  }
}
