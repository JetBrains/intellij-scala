package org.jetbrains.plugins.scala
package console


import com.intellij.execution.configurations.{ConfigurationFactory, JavaParameters, _}
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.{CantRunException, ExecutionException, Executor}
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.plugins.scala.project._

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

class ScalaConsoleRunConfiguration(project: Project, configurationFactory: ConfigurationFactory, name: String)
  extends ModuleBasedConfiguration[RunConfigurationModule, Element](name, new RunConfigurationModule(project), configurationFactory) {

  private val MainClass = "scala.tools.nsc.MainGenericRunner"
  private val DefaultJavaOptions = "-Djline.terminal=NONE"
  private val UseJavaCp = "-usejavacp"

  @BeanProperty var myConsoleArgs: String = ""
  @BeanProperty var workingDirectory: String = Option(getProject.baseDir).map(_.getPath).getOrElse("")
  @BeanProperty var javaOptions: String = DefaultJavaOptions

  def consoleArgs: String = ensureUsesJavaCpByDefault(this.myConsoleArgs)
  def consoleArgs_=(s: String): Unit = this.myConsoleArgs = ensureUsesJavaCpByDefault(s)
  private def ensureUsesJavaCpByDefault(s: String): String = if (s == null || s.isEmpty) UseJavaCp else s

  private def getModule: Module = getConfigurationModule.getModule

  override protected def getValidModules: java.util.List[Module] = getProject.modulesWithScala.toList.asJava

  def apply(params: ScalaConsoleRunConfigurationForm): Unit = {
    javaOptions = params.getJavaOptions
    consoleArgs = params.getConsoleArgs
    workingDirectory = params.getWorkingDirectory
    setModule(params.getModule)
  }

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] =
    new ScalaConsoleRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    XmlSerializer.serializeInto(this, element)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    XmlSerializer.deserializeInto(this, element)
  }

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = createParams
        params.getProgramParametersList.addParametersString(consoleArgs)
        params
      }
    }

    val consoleBuilder = new TextConsoleBuilderImpl(project) {
      override def getConsole: ConsoleView = ScalaLanguageConsoleBuilder.createConsole(project)
    }
    state.setConsoleBuilder(consoleBuilder)
    state
  }

  private def createParams: JavaParameters = {
    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    new JavaParameters() {
      getVMParametersList.addParametersString(javaOptions)
      getClassPath.addScalaClassPath(module)
      setUseDynamicClasspath(JdkUtil.useDynamicClasspath(getProject))
      getClassPath.addRunners()
      setWorkingDirectory(workingDirectory)
      setMainClass(MainClass)
      configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
    }
  }

}
