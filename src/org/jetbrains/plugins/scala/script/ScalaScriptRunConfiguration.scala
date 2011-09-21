package org.jetbrains.plugins.scala
package script

import collection.mutable.{HashSet, ArrayBuffer}
import com.intellij.compiler.impl.javaCompiler.ModuleChunk
import com.intellij.compiler.ModuleCompilerUtil
import com.intellij.execution.configurations._
import com.intellij.execution.filters._
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{Project}

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiManager, PsiFile}
import java.io.{File}
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.openapi.roots.{OrderEntry, ModuleOrderEntry, OrderRootType, ModuleRootManager}

import com.intellij.openapi.util.{JDOMExternalizer}
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.{ConsoleViewContentType, ConsoleView}
import com.intellij.execution.{CantRunException, ExecutionException, Executor}
import com.intellij.facet.FacetManager
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.editor.actions.EnterAction
import com.intellij.openapi.module.{ModuleUtil, ModuleManager, Module}
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.projectRoots.{JavaSdkType, Sdk}
import com.intellij.util.PathUtil
import compiler.rt.ScalacRunner
import lang.psi.api.ScalaFile
import util.{ScalaUtils}



import com.intellij.vcsUtil.VcsUtil
import java.util.{Arrays, Collection}
import org.jdom.Element
import config.ScalaFacet
import collection.JavaConversions._

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
  private var consoleArgs = ""
  private var workingDirectory = {
    val base = getProject.getBaseDir
    if (base != null) base.getPath
    else ""
  }

  def getScriptPath = scriptPath
  def getScriptArgs = scriptArgs
  def getJavaOptions = javaOptions
  def getConsoleArgs = consoleArgs
  def getWorkingDirectory: String = workingDirectory
  def setScriptPath(s: String): Unit = scriptPath = s
  def setScriptArgs(s: String): Unit = scriptArgs = s
  def setJavaOptions(s: String): Unit = javaOptions = s
  def setConsoleArgs(s: String): Unit = consoleArgs = s
  def setWorkingDirectory(s: String): Unit = workingDirectory = s

  def apply(params: ScalaScriptRunConfigurationForm) {
    setScriptArgs(params.getScriptArgs)
    setScriptPath(params.getScriptPath)
    setJavaOptions(params.getJavaOptions)
    setConsoleArgs(params.getConsoleArgs)
    setWorkingDirectory(params.getWorkingDirectory)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    def fileNotFoundError {
      throw new ExecutionException("Scala script file not found.")
    }
    try {
      val file: VirtualFile = VcsUtil.getVirtualFile(scriptPath)
      PsiManager.getInstance(project).findFile(file) match {
        case f: ScalaFile if f.isScriptFile() =>
        case _ => fileNotFoundError
      }
    }
    catch {
      case e => fileNotFoundError
    }

    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val facet = ScalaFacet.findIn(module).getOrElse {
      throw new ExecutionException("No Scala facet configured for module " + module.getName)
    }

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
        params.setWorkingDirectory(getWorkingDirectory)
//        params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
//        params.getVMParametersList.add(SCALA_HOME  + scalaSdkPath)
        params.getVMParametersList.add(CLASSPATH)
        params.getVMParametersList.add(EMACS)

        params.getClassPath.addAllFiles(facet.files)

        params.setMainClass(MAIN_CLASS)
        params.getProgramParametersList.add("-nocompdaemon") //todo: seems to be a bug in scala compiler. Ticket #1498
        params.getProgramParametersList.add("-classpath")
        params.getProgramParametersList.add(getClassPath(module))
        params.getProgramParametersList.addAll(getConsoleArgs.trim.split("""\s+"""): _*)
        params.getProgramParametersList.add(scriptPath)
        params.getProgramParametersList.addParametersString(scriptArgs)
        return params
      }
    }

    val consoleBuilder = TextConsoleBuilderFactory.getInstance.createBuilder(getProject)
    consoleBuilder.addFilter(getFilter(script))
    state.setConsoleBuilder(consoleBuilder);
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

  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaScriptRunConfigurationEditor(project, this)

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "path", getScriptPath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "consoleargs", getConsoleArgs)
    JDOMExternalizer.write(element, "params", getScriptArgs)
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    readModule(element)
    scriptPath = JDOMExternalizer.readString(element, "path")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    scriptArgs = JDOMExternalizer.readString(element, "params")
    consoleArgs = JDOMExternalizer.readString(element, "consoleargs")
    val pp = JDOMExternalizer.readString(element, "workingDirectory")
    if (pp != null) workingDirectory = pp
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

  private def getFilter(file: VirtualFile): Filter = {
    import Filter._
    new Filter {
      def applyFilter(line: String, entireLength: Int): Result = {
        var start = entireLength - line.length
        var end = entireLength - line.length
        if (line.startsWith("(fragment of ")) {
          try {
            var cache = line.replaceFirst("[(][f][r][a][g][m][e][n][t][ ][o][f][ ]", "")
            val fileName = cache.substring(0, cache.indexOf("):"))
            cache = cache.replaceFirst("[^)]*[)][:]", "")
            val lineNumber = Integer.parseInt(cache.substring(0, cache.indexOf(":")))
            cache = cache.replaceFirst("[^:]", "")
            end += line.length - cache.length
            val hyperlink = new OpenFileHyperlinkInfo(getProject, file, lineNumber-1)
            return new Result(start, end, hyperlink)
          }
          catch {
            case _ => return null
          }
        } else return null
      }
    }
  }
}