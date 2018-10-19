package org.jetbrains.plugins.scala
package runner

import com.intellij.execution.configurations.{ConfigurationFactory, JavaParameters, ModuleBasedConfiguration, RunConfigurationModule}
import com.intellij.execution.{CantRunException, ExecutionException}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.JDOMExternalizer
import org.jdom.Element
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.collection.JavaConverters._

/**
  */
abstract class BaseRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule, Element](name, new RunConfigurationModule(project), configurationFactory) {
  def mainClass:String
  val defaultJavaOptions = "-Djline.terminal=NONE"
  val useJavaCp = "-usejavacp"
  def ensureUsesJavaCpByDefault(s: String): String = if (s == null || s.isEmpty) useJavaCp else s
  private var myConsoleArgs = ""
  def consoleArgs: String = ensureUsesJavaCpByDefault(this.myConsoleArgs)
  def consoleArgs_=(s: String): Unit = this.myConsoleArgs = ensureUsesJavaCpByDefault(s)
  var javaOptions = defaultJavaOptions
  var workingDirectory = Option(getProject.getBaseDir) map (_.getPath) getOrElse ""
  def getModule: Module = getConfigurationModule.getModule
  def getValidModules: java.util.List[Module] = getProject.modulesWithScala.toList.asJava

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "vmparams4", javaOptions)
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    javaOptions = JDOMExternalizer.readString(element, "vmparams4")
    if (javaOptions == null) {
      javaOptions = JDOMExternalizer.readString(element, "vmparams")
      if (javaOptions != null) javaOptions += s" $defaultJavaOptions"
    }
    val str = JDOMExternalizer.readString(element, "workingDirectory")
    if (str != null)
      workingDirectory = str
  }

  def createParams: JavaParameters = {
    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val scalaSdk = module.scalaSdk.getOrElse {
      throw new ExecutionException("No Scala facet configured for module " + module.getName)
    }

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    val params = new JavaParameters()
    params.getVMParametersList.addParametersString(javaOptions)

    val files = scalaSdk.compilerClasspath

    params.getClassPath.addAllFiles(files.asJava)
    params.setUseDynamicClasspath(JdkUtil.useDynamicClasspath(getProject))
    params.getClassPath.add(ScalaUtil.runnersPath())
    params.setWorkingDirectory(workingDirectory)
    params.setMainClass(mainClass)
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
    params
  }

}
