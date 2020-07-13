package org.jetbrains.bsp.project.test

import java.net.URI
import java.util.Collections
import java.{util => j}

import com.intellij.execution.Executor
import com.intellij.execution.configurations._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.{ConfigurationException, SettingsEditor}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import javax.swing.Icon
import org.jdom.Element
import org.jetbrains.bsp.{BspBundle, Icons}
import org.jetbrains.bsp.project.test.BspTestConfigurationForm._

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

class BspTestRunType extends ConfigurationType {
  override def getDisplayName: String = BspBundle.message("bsp.test")

  override def getConfigurationTypeDescription: String = getDisplayName

  override def getIcon: Icon = Icons.BSP

  override def getId: String = "BSP_TEST_RUN_CONFIGURATION"

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array(new BspTestRunFactory(this))
}

class BspTestRunFactory(t: ConfigurationType) extends ConfigurationFactory(t) {

  override def getId: String = "BSP test"

  override def createTemplateConfiguration(project: Project): RunConfiguration = new BspTestRunConfiguration(project, this, "BSP_TEST_RUN")

  override def getName: String = "BspTestRunFactory"
}



class BspNoMatchingClassException() extends ConfigurationException(
  BspBundle.message("bsp.test.no.class.matches.with.the.regex"),
  BspBundle.message("the.test.class.was.not.reported.by.bsp.endpoint"))

class TestClass(@BeanProperty var target: String, @BeanProperty var classes: j.List[String]) {
  def this() = this("", Collections.emptyList())
}

class BspTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
  extends RunConfigurationBase[String](project, configurationFactory, name) {


  @BeanProperty var testMode: TestMode = TestMode.ALL_IN_PROJECT
  @BeanProperty var lastTestClassesResponse: j.List[TestClass] = Collections.emptyList()
  @BeanProperty var matchedTestClasses: j.Map[String, j.List[String]] = Collections.emptyMap()
  @BeanProperty var testClassesRegex = ".*"

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    XmlSerializer.deserializeInto(this, element)
  }

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    XmlSerializer.serializeInto(this, element)
  }

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new BspTestConfigurationForm(getProject)

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = {
    val scalaMap = matchedTestClasses.asScala.map { case (uri, list) => (URI.create(uri), list.asScala.toList) }.toMap
    new BspTestRunner(getProject, this, executor, {
      testMode match {
        case TestMode.ALL_IN_PROJECT => None
        case TestMode.CLASS => Some(scalaMap)
      }
    })
  }
}