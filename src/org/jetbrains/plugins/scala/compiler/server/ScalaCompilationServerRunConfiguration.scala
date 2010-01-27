package org.jetbrains.plugins.scala.compiler.server

import com.intellij.openapi.options.SettingsEditor
import com.intellij.execution.configurations._
import com.intellij.openapi.module.{ModuleManager, Module}
import com.intellij.facet.FacetManager
import com.intellij.execution.{CantRunException, ExecutionException, Executor}
import com.intellij.openapi.projectRoots.JavaSdkType
import org.jetbrains.plugins.scala.config.{ScalaCompilerUtil, ScalaConfigUtils}
import com.intellij.vcsUtil.VcsUtil
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.execution.filters.{TextConsoleBuilderFactory}
import collection.mutable.{HashSet, ArrayBuffer}
import com.intellij.openapi.roots.{ModuleRootManager}
import java.util.Arrays
import com.intellij.openapi.util.JDOMExternalizer
import org.jdom.Element

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.2010
 */

class ScalaCompilationServerRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS = "scala.tools.nsc.CompileServer"
  private var javaOptions = ""

  def getJavaOptions = javaOptions
  def setJavaOptions(s: String): Unit = javaOptions = s

  def apply(params: ScalaCompilationServerRunConfigurationForm) {
    setJavaOptions(params.getJavaOptions)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {

    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val jarPath = ScalaConfigUtils.getScalaSdkJarPath(getModule)
    val compilerJarPath = ScalaCompilerUtil.getScalaCompilerJarPath(getModule)
    if (jarPath == "" || compilerJarPath == "") throw new ExecutionException("Scala SDK is not specified")

    val rootManager = ModuleRootManager.getInstance(module);
    val sdk = rootManager.getSdk();
    if (sdk == null || !(sdk.getSdkType.isInstanceOf[JavaSdkType])) {
      throw CantRunException.noJdkForModule(module);
    }
    val sdkType = sdk.getSdkType

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters();

        params.setJdk(sdk)

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
//        params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
//        params.getVMParametersList.add(SCALA_HOME  + scalaSdkPath)
        params.getVMParametersList.add(CLASSPATH)
        params.getVMParametersList.add(EMACS)

        val sdkJar = VcsUtil.getVirtualFile(jarPath)
        if (sdkJar != null) {
          params.getClassPath.add(sdkJar)
        }

        val compilerJar = VcsUtil.getVirtualFile(compilerJarPath)
        if (sdkJar != null) {
          params.getClassPath.add(compilerJar)
        }

        params.setMainClass(MAIN_CLASS)
        return params
      }
    }

    val consoleBuilder = TextConsoleBuilderFactory.getInstance.createBuilder(getProject)
    state.setConsoleBuilder(consoleBuilder);
    return state;
  }

  def getModule: Module = {
    var module: Module = null
    //todo:
    if (module == null) module = getConfigurationModule.getModule
    return module
  }

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new ScalaCompilationServerRunConfiguration(getProject, getFactory, getName)

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

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaCompilationServerRunConfigurationEditor(project, this)

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    readModule(element)
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
  }
}