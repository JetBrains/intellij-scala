package org.jetbrains.plugins.scala
package runner

import com.intellij.execution.configurations.{ConfigurationFactory, JavaParameters, ModuleBasedConfiguration, RunConfigurationModule}
import com.intellij.execution.{CantRunException, ExecutionException}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.plugins.scala.project._

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

/**
  */
abstract class BaseRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule, Element](name, new RunConfigurationModule(project), configurationFactory) {
  private val defaultJavaOptions = "-Djline.terminal=NONE"
  private val useJavaCp = "-usejavacp"
  protected def mainClass:String
  def ensureUsesJavaCpByDefault(s: String): String = if (s == null || s.isEmpty) useJavaCp else s
  def consoleArgs: String = ensureUsesJavaCpByDefault(this.myConsoleArgs)
  def consoleArgs_=(s: String): Unit = this.myConsoleArgs = ensureUsesJavaCpByDefault(s)
  def getModule: Module = getConfigurationModule.getModule
  def getValidModules: java.util.List[Module] = getProject.modulesWithScala.toList.asJava

  @BeanProperty var myConsoleArgs: String     = ""
  @BeanProperty var workingDirectory: String  = Option(getProject.baseDir) map (_.getPath) getOrElse ""
  @BeanProperty var javaOptions: String       = defaultJavaOptions

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    XmlSerializer.serializeInto(this, element)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    XmlSerializer.deserializeInto(this, element)
  }

  def createParams: JavaParameters = {
    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    val params = new JavaParameters()
    params.getVMParametersList.addParametersString(javaOptions)

    params.getClassPath.addScalaClassPath(module)
    params.setUseDynamicClasspath(JdkUtil.useDynamicClasspath(getProject))
    params.getClassPath.addRunners()
    params.setWorkingDirectory(workingDirectory)
    params.setMainClass(mainClass)
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
    params
  }

}
