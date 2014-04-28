package org.jetbrains.plugins.scala
package runner

import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.{JavaParameters, RunConfigurationModule, ModuleBasedConfiguration, ConfigurationFactory}
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.config.{Libraries, CompilerLibraryData, ScalaFacet}
import scala.collection.JavaConverters._
import org.jdom.Element
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.execution.{CantRunException, ExecutionException}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.projectRoots.{JdkUtil, JavaSdkType}
import org.jetbrains.plugins.scala.compiler.ScalacSettings
import com.intellij.util.PathUtil

/**
  */
abstract class BaseRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  def mainClass:String
  val defaultJavaOptions = "-Djline.terminal=NONE"
  val useJavaCp = "-usejavacp"
  def ensureUsesJavaCpByDefault(s: String) = if (s == null || s.isEmpty) useJavaCp else s
  private var myConsoleArgs = ""
  def consoleArgs = ensureUsesJavaCpByDefault(this.myConsoleArgs)
  def consoleArgs_=(s: String) = this.myConsoleArgs = ensureUsesJavaCpByDefault(s)
  var javaOptions = defaultJavaOptions
  var workingDirectory = Option(getProject.getBaseDir) map (_.getPath) getOrElse ""
  def getModule: Module = getConfigurationModule.getModule
  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList.asJava

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    writeModule(element)
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

    val facet = ScalaFacet.findIn(module).getOrElse {
      throw new ExecutionException("No Scala facet configured for module " + module.getName)
    }

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    val params = new JavaParameters()
    params.getVMParametersList.addParametersString(javaOptions)
    val files =
      if (facet.fsc) {
        val settings = ScalacSettings.getInstance(getProject)
        val lib: Option[CompilerLibraryData] = Libraries.findBy(settings.COMPILER_LIBRARY_NAME,
          settings.COMPILER_LIBRARY_LEVEL, getProject)
        lib match {
          case Some(compilerLib) => compilerLib.files
          case _ => facet.files
        }
      } else facet.files
    params.getClassPath.addAllFiles(files.asJava)
    params.setUseDynamicClasspath(JdkUtil.useDynamicClasspath(getProject))
    params.setUseDynamicVMOptions(JdkUtil.useDynamicVMOptions())
    params.getClassPath.add(PathUtil.getJarPathForClass(classOf[_root_.org.jetbrains.plugins.scala.worksheet.MyWorksheetRunner]))
    params.setWorkingDirectory(workingDirectory)
    params.setMainClass(mainClass)
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
    params
  }

}
