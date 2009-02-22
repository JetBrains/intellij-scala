package org.jetbrains.plugins.scala.testingSupport.scalaTest


import _root_.java.io.File
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.openapi.project.Project
import jdom.Element
import _root_.scala.collection.mutable.HashSet
import com.intellij.openapi.module.{ModuleUtil, ModuleManager, Module}
import com.intellij.openapi.options.SettingsEditor
import config.{ScalaConfigUtils, ScalaSDK}

import com.intellij.execution.configurations._
import _root_.java.util.Arrays
import com.intellij.facet.FacetManager
import com.intellij.execution.{CantRunException, ExecutionException, Executor}
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}

import com.intellij.openapi.projectRoots.JavaSdkType
import lang.psi.api.ScalaFile
import com.intellij.psi.PsiManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.filters.TextConsoleBuilderFactory


import com.intellij.vcsUtil.VcsUtil
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import com.intellij.openapi.util.JDOMExternalizer
import script.ScalaScriptRunConfiguration

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS = "scala.tools.nsc.MainGenericRunner"
  private var testClassPath = ""
  private var scriptArgs = ""
  private var javaOptions = ""

  def getScriptPath = testClassPath
  def getScriptArgs = scriptArgs
  def getJavaOptions = javaOptions
  def setScriptPath(s: String): Unit = testClassPath = s
  def setScriptArgs(s: String): Unit = scriptArgs = s
  def setJavaOptions(s: String): Unit = javaOptions = s

  def apply(params: ScalaTestRunConfigurationForm) {
    //todo:
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    def fileNotFoundError {
      throw new ExecutionException("Scala script file not found.")
    }
    try {
      val file: VirtualFile = VcsUtil.getVirtualFile(testClassPath)
      PsiManager.getInstance(project).findFile(file) match {
        case f: ScalaFile if f.isScriptFile =>
        case _ => fileNotFoundError
      }
    }
    catch {
      case e => fileNotFoundError
    }

    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")
    val scalaSdkPath = getScalaInstallPath
    if (scalaSdkPath == "") throw new ExecutionException("Scala SDK is not specified")
    val rootManager = ModuleRootManager.getInstance(module);
    val sdk = rootManager.getSdk();
    if (sdk == null || !(sdk.getSdkType.isInstanceOf[JavaSdkType])) {
      throw CantRunException.noJdkForModule(module);
    }
    val sdkType = sdk.getSdkType

    val script = VcsUtil.getVirtualFile(testClassPath)
    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters();

        params.setJdk(sdk)

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        //params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
        params.getVMParametersList.add(SCALA_HOME  + scalaSdkPath)
        params.getVMParametersList.add(CLASSPATH)
        params.getVMParametersList.add(EMACS)
        val list = new java.util.ArrayList[String]
        val dir: VirtualFile = VcsUtil.getVirtualFile(scalaSdkPath + File.separator + "lib")
        for (child <- dir.getChildren) {
          params.getClassPath.add(child)
        }
        params.setMainClass(MAIN_CLASS)

        params.getProgramParametersList.add("-nocompdaemon")
        params.getProgramParametersList.add("-classpath")
        params.getProgramParametersList.add(getClassPath(module))
        params.getProgramParametersList.add(testClassPath)
        params.getProgramParametersList.addParametersString(scriptArgs)
        return params
      }
    }

    val consoleBuilder = TextConsoleBuilderFactory.getInstance.createBuilder(getProject)
    state.setConsoleBuilder(consoleBuilder);
    return state;
  }

  def getModule: Module = {
    var module: Module = null
    try {
      val file: VirtualFile = VcsUtil.getVirtualFile(testClassPath)
      module = ModuleUtil.findModuleForFile(file, getProject)
    }
    catch {
      case e =>
    }
    if (module == null) module = getConfigurationModule.getModule
    return module
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new ScalaTestRunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = {
    val result = new ArrayBuffer[Module]

    val modules = ModuleManager.getInstance(getProject).getModules
    for (module <- modules) {
      val facetManager = FacetManager.getInstance(module)
      if (facetManager.getFacetByType(org.jetbrains.plugins.scala.config.ScalaFacet.ID) != null) {
        result += module
      }
    }
    return Arrays.asList(result.toArray: _*)
  }

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaTestRunConfigurationEditor(project, this)

  def getSdk(): ScalaSDK = {
    if (getModule != null) ScalaConfigUtils.getScalaSDKs(getModule).apply(0)
    else null
  }

  def getScalaInstallPath(): String = {
    if (getModule != null) {
      val sdk = ScalaConfigUtils.getScalaInstallPath(getModule)
      var exePath = sdk.replace('\\', File.separatorChar)
      //if (!exePath.endsWith(File.separator)) exePath += File.separator
      return exePath
    }
    else ""
  }


  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "path", getScriptPath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "params", getScriptArgs)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    readModule(element)
    testClassPath = JDOMExternalizer.readString(element, "path")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    scriptArgs = JDOMExternalizer.readString(element, "params")
  }

  private def getClassPath(module: Module): String = {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val entries = moduleRootManager.getOrderEntries
    val cpVFiles = new HashSet[VirtualFile];
    for (orderEntry <- entries) {
      cpVFiles ++= orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)
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
    return res.toString
  }
}