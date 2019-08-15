package org.jetbrains.bsp.project.test

import java.net.URI

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
  override def getDisplayName: String = "BSP test run"

  override def getConfigurationTypeDescription: String = getDisplayName

  override def getIcon: Icon = Icons.BSP

  override def getId: String = "BSP_TEST_RUN_CONFIGURATION"

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array(new BspTestRunFactory(this))
}

class BspTestRunFactory(t: ConfigurationType) extends ConfigurationFactory(t) {
  override def createTemplateConfiguration(project: Project): RunConfiguration = new BspTestRunConfiguration(project, this, "BSP_TEST_RUN")

  override def getName: String = "BspTestRunFactory"
}

class BspTestSettingsEditor extends SettingsEditor[BspTestRunConfiguration] {

  val form = new BspTestConfigurationForm

  override def resetEditorFrom(settings: BspTestRunConfiguration): Unit = form(settings)

  override def applyEditorTo(settings: BspTestRunConfiguration): Unit = settings(form)

  override def createEditor(): JComponent = form.mainPanel
}


class BspTestClassNotFoundException(klasName: String) extends ConfigurationException(
  "Unknown test class",
  s"The test class $klasName was not reported by BSP endpoint buildTarget/scalaTestClasses (try to reimport the project)")

class BspTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
  extends RunConfigurationBase[String](project, configurationFactory, name) {

  var testSelection: SelectionMode = SelectionMode.ALL_IN_PROJECT
  // Empty map means run all tests
  var testClasses: Map[URI, List[String]] = Map.empty

  def apply(settings: BspTestConfigurationForm): Unit = {
    testSelection = settings.getTestSelectionMode
    testClasses = testSelection match {
      case SelectionMode.CLASS =>
        val testClassName = settings.getTestClassName
        BspMetadata.findScalaTestClasses(project)
          .filter({ case (_, klas) => klas == testClassName })
          .find(_ => true)
          .orElse(throw new BspTestClassNotFoundException(testClassName))
          .map { case (l, r) => (l, List(r)) }
          .toMap
      case SelectionMode.ALL_IN_PROJECT => Map.empty
    }
  }

  def getTestClassName(): String = {
    testClasses.flatMap(x => x._2.find(_ => true)).find(_ => true)
      .getOrElse("")
  }

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new BspTestSettingsEditor()

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    new BspTestRunner(getProject, this, executor, testClasses)
}