package org.jetbrains.plugins.scala
package console

import com.intellij.execution.configurations._
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import java.lang.String
import org.jdom.Element
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.{JdkUtil, JavaSdkType}
import java.io._
import collection.mutable.HashSet
import collection.JavaConversions._
import com.intellij.openapi.roots.{CompilerModuleExtension, OrderRootType, ModuleRootManager}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.execution.{CantRunException, ExecutionException, Executor}
import configuration._

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaConsoleRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS = "org.jetbrains.plugins.scala.compiler.rt.ConsoleRunner"
  private var javaOptions = "-Djline.terminal=NONE"
  private var consoleArgs = "-usejavacp"
  private var workingDirectory = Option(getProject.getBaseDir) map (_.getPath) getOrElse ""
  

  def getJavaOptions = javaOptions

  def setJavaOptions(s: String) {javaOptions = s}

  def getConsoleArgs: String = consoleArgs

  def setConsoleArgs(s: String) {consoleArgs = s}

  def getWorkingDirectory = workingDirectory

  def setWorkingDirecoty(s: String) {workingDirectory = s}

  def apply(params: ScalaConsoleRunConfigurationForm) {
    setJavaOptions(params.getJavaOptions)
    setConsoleArgs(params.getConsoleArgs)
    setWorkingDirecoty(params.getWorkingDirectory)

    setModule(params.getModule)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !(sdk.getSdkType.isInstanceOf[JavaSdkType])) {
      throw CantRunException.noJdkForModule(module)
    }

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters()

        params.setJdk(sdk)

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        //params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
//        params.getVMParametersList.add(SCALA_HOME  + scalaSdkPath)

        params.getClassPath.addAllFiles(module.scalaSdk.map(_.compilerClasspath).getOrElse(Seq.empty))

        val rtJarPath = PathUtil.getJarPathForClass(classOf[_root_.org.jetbrains.plugins.scala.compiler.rt.ConsoleRunner])
        params.getClassPath.add(rtJarPath)
        params.setWorkingDirectory(workingDirectory)
        params.setMainClass(MAIN_CLASS)
        if (JdkUtil.useDynamicClasspath(getProject)) {
          try {
            val fileWithParams: File = File.createTempFile("scalaconsole", ".tmp")
            val printer: PrintStream = new PrintStream(new FileOutputStream(fileWithParams))
            printer.println("-classpath")
            printer.println(getClassPath(module))
            val parms: Array[String] = ParametersList.parse(getConsoleArgs)
            for (parm <- parms) {
              printer.println(parm)
            }
            printer.close()
            params.getProgramParametersList.add("@" + fileWithParams.getPath)
          }
          catch {
            case ignore: IOException => {
            }
          }
        } else {
          params.getProgramParametersList.add("-classpath")
          params.getProgramParametersList.add(getClassPath(module))
          params.getProgramParametersList.addParametersString(getConsoleArgs)
        }
        params
      }
    }

    val consoleBuilder = new TextConsoleBuilderImpl(project) {
      override def getConsole: ConsoleView = {
        val consoleView = new ScalaLanguageConsoleView(getProject)
        consoleView.getConsole.setPrompt(null)
        consoleView
      }
    }
    state.setConsoleBuilder(consoleBuilder)
    state
  }

  def getModule: Module = getConfigurationModule.getModule

  def getValidModules: java.util.List[Module] = getProject.modulesWithScala

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaConsoleRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "vmparams4", getJavaOptions)
    JDOMExternalizer.write(element, "consoleArgs", getConsoleArgs)
    JDOMExternalizer.write(element, "workingDirectory", getWorkingDirectory)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    javaOptions = JDOMExternalizer.readString(element, "vmparams4")
    if (javaOptions == null) {
      javaOptions = JDOMExternalizer.readString(element, "vmparams")
      if (javaOptions != null) javaOptions += " -Djline.terminal=NONE"
    }
    consoleArgs = JDOMExternalizer.readString(element, "consoleArgs")
    val str = JDOMExternalizer.readString(element, "workingDirectory")
    if (str != null)
      workingDirectory = str
  }

  private def getClassPath(module: Module): String = {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val entries = moduleRootManager.getOrderEntries
    val cpVFiles = new HashSet[VirtualFile]
    cpVFiles ++= CompilerModuleExtension.getInstance(module).getOutputRoots(true)
    for (orderEntry <- entries) {
      cpVFiles ++= orderEntry.getFiles(OrderRootType.CLASSES)
    }
    val res = new StringBuilder("")
    for (file <- cpVFiles) {
      var path = file.getPath
      val jarSeparatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR)
      if (jarSeparatorIndex > 0) {
        path = path.substring(0, jarSeparatorIndex)
      }
      res.append(path).append(File.pathSeparator)
    }
    res.toString()
  }
}