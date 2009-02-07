package org.jetbrains.plugins.scala.script


import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.collection.mutable.HashSet
import com.intellij.compiler.impl.javaCompiler.ModuleChunk
import com.intellij.compiler.ModuleCompilerUtil
import com.intellij.execution.configurations._
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{CantRunException, ExecutionException, Executor}
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.{ModuleUtil, ModuleManager, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.projectRoots.{JavaSdkType, Sdk}
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.openapi.roots.{OrderEntry, ModuleOrderEntry, OrderRootType, ModuleRootManager}


import util.{ScalaUtils}



import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.psi.PsiManager
import com.intellij.vcsUtil.VcsUtil
import config.{ScalaConfigUtils, ScalaSDK}
import java.io.{File}

import java.util.{Arrays, Collection}
import jdom.Element
/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS = "scala.tools.nsc.MainGenericRunner"
  private var scriptPath = ""
  private var scriptArgs = ""
  private var javaOptions = ""

  def getScriptPath = scriptPath
  def getScriptArgs = scriptArgs
  def getJavaOptions = javaOptions
  def setScriptPath(s: String): Unit = scriptPath = s
  def setScriptArgs(s: String): Unit = scriptArgs = s
  def setJavaOptions(s: String): Unit = javaOptions = s

  def apply(params: ScalaScriptRunConfigurationForm) {
    setScriptArgs(params.getScriptArgs)
    setScriptPath(params.getScriptPath)
    setJavaOptions(params.getJavaOptions)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
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

    val script = VcsUtil.getVirtualFile(scriptPath)
    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters();

        params.setJdk(sdk)

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        //params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5008")
        params.getVMParametersList.add(SCALA_HOME  + scalaSdkPath)
        params.getVMParametersList.add(CLASSPATH)
        params.getVMParametersList.add(EMACS)
        //params.getVMParametersList.add("-nocompdaemon")
        val list = new java.util.ArrayList[String]
        val dir: VirtualFile = VcsUtil.getVirtualFile(scalaSdkPath + File.separator + "lib")
        for (child <- dir.getChildren) {
          params.getClassPath.add(child)
        }
        params.setMainClass(MAIN_CLASS)

        params.getProgramParametersList.add("-nocompdaemon") //todo: seems to be a bug in scala compiler. Ticket #1498
        params.getProgramParametersList.add("-classpath")
        params.getProgramParametersList.add(getClassPath(module))
        params.getProgramParametersList.add(scriptPath)
        params.getProgramParametersList.addParametersString(scriptArgs)
        return params
      }
    }

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;
  }

  def getModule: Module = {
    var module: Module = null
    try {
      val file: VirtualFile = VcsUtil.getVirtualFile(scriptPath)
      module = ModuleUtil.findModuleForFile(file, getProject)
    }
    catch {
      case e =>
    }
    if (module == null) module = getConfigurationModule.getModule
    return module
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new ScalaScriptRunConfiguration(getProject, getFactory, getName)

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

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaScriptRunConfigurationEditor(project, this)

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
    scriptPath = JDOMExternalizer.readString(element, "path")
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