package org.jetbrains.bsp.project.test

import java.net.URI
import java.util.regex.{Pattern, PatternSyntaxException}

import com.intellij.execution.Executor
import com.intellij.execution.configurations._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.{ConfigurationException, SettingsEditor}
import com.intellij.openapi.project.Project
import javax.swing.{Icon, JComponent}
import org.jetbrains.bsp.Icons
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.project.test.BspTestConfigurationForm._

class BspTestRunType extends ConfigurationType {
  override def getDisplayName: String = "BSP test"

  override def getConfigurationTypeDescription: String = getDisplayName

  override def getIcon: Icon = Icons.BSP

  override def getId: String = "BSP_TEST_RUN_CONFIGURATION"

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array(new BspTestRunFactory(this))
}

class BspTestRunFactory(t: ConfigurationType) extends ConfigurationFactory(t) {
  override def createTemplateConfiguration(project: Project): RunConfiguration = new BspTestRunConfiguration(project, this, "BSP_TEST_RUN")

  override def getName: String = "BspTestRunFactory"
}

class BspTestSettingsEditor(val proj: Project) extends SettingsEditor[BspTestRunConfiguration] {

  val form = new BspTestConfigurationForm(proj)

  override def resetEditorFrom(settings: BspTestRunConfiguration): Unit = form(settings)

  override def applyEditorTo(settings: BspTestRunConfiguration): Unit = settings(form)

  override def createEditor(): JComponent = form.mainPanel
}


class BspNoMatchingClassException() extends ConfigurationException(
  "No class is matching with the regex",
  s"The test class  was not reported by BSP endpoint buildTarget/scalaTestClasses (try to reimport the project)")


class BspTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
  extends RunConfigurationBase[String](project, configurationFactory, name) {

  var testMode: TestMode = TestMode.ALL_IN_PROJECT
  var matchedTestClasses: Map[URI, List[String]] = Map.empty
  var testClassesRegex = ".*"

  def apply(settings: BspTestConfigurationForm): Unit = {
    testMode = settings.getTestMode
    testClassesRegex = settings.getTestClassNameRegex
    matchedTestClasses = testMode match {
      case TestMode.CLASS =>
        val testClassRegex = try {
          Pattern.compile(testClassesRegex)
        } catch {
          case _: PatternSyntaxException => throw new BspNoMatchingClassException
        }
        val matching = BspMetadata.findScalaTestClasses(project)
          .filter({ case (_, klas) => testClassRegex.matcher(klas).matches() })
        val map = matching.groupBy(_._1).mapValues(l => l.map(_._2).toList)
        if (map.isEmpty)
          throw new BspNoMatchingClassException
        map
      case TestMode.ALL_IN_PROJECT => Map.empty
    }
  }

  def getTestClassesRegex: String = testClassesRegex

  def getTestMode: TestMode = testMode

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new BspTestSettingsEditor(getProject)

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    new BspTestRunner(getProject, this, executor, {
      testMode match {
        case TestMode.ALL_IN_PROJECT => None
        case TestMode.CLASS => Some(matchedTestClasses)
      }
    })
}