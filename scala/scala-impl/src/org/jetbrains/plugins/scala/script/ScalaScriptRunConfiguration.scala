package org.jetbrains.plugins.scala
package script

import com.intellij.execution.configurations._
import com.intellij.execution.filters._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{ExecutionException, Executor}
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.refactoring.listeners.{RefactoringElementAdapter, RefactoringElementListener}
import com.intellij.vcsUtil.VcsUtil
import org.jdom.Element
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project._

import scala.collection.JavaConverters._

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) with RefactoringListenerProvider {
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

  def getScriptPath: String = scriptPath
  def getScriptArgs: String = scriptArgs
  def getJavaOptions: String = javaOptions
  def getConsoleArgs: String = consoleArgs
  def getWorkingDirectory: String = workingDirectory
  def setScriptPath(s: String) {
    scriptPath = s
  }
  def setScriptArgs(s: String) {
    scriptArgs = s
  }
  def setJavaOptions(s: String) {
    javaOptions = s
  }
  def setConsoleArgs(s: String) {
    consoleArgs = s
  }
  def setWorkingDirectory(s: String) {
    workingDirectory = s
  }

  def apply(params: ScalaScriptRunConfigurationForm) {
    setScriptArgs(params.getScriptArgs)
    setScriptPath(params.getScriptPath)
    setJavaOptions(params.getJavaOptions)
    setConsoleArgs(params.getConsoleArgs)
    setWorkingDirectory(params.getWorkingDirectory)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    def fileNotFoundError() {
      throw new ExecutionException("Scala script file not found.")
    }
    try {
      val file: VirtualFile = VcsUtil.getVirtualFile(scriptPath)
      PsiManager.getInstance(project).findFile(file) match {
        case f: ScalaFile if f.isScriptFile && !f.isWorksheetFile =>
        case _ => fileNotFoundError()
      }
    }
    catch {
      case _: Exception => fileNotFoundError()
    }

    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val script = VcsUtil.getVirtualFile(scriptPath)
    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters()

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        params.setWorkingDirectory(getWorkingDirectory)
        params.getVMParametersList.add(CLASSPATH)
        params.getVMParametersList.add(EMACS)

        params.setMainClass(MAIN_CLASS)
        params.getProgramParametersList.add("-nocompdaemon") //todo: seems to be a bug in scala compiler. Ticket #1498
        params.getProgramParametersList.add("-classpath")
        params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
        params.getProgramParametersList.add(params.getClassPath.getPathsString)
        params.getClassPath.addAllFiles(
          module.scalaSdk
            .map(_.compilerClasspath)
            .getOrElse(Seq.empty)
            .asJava
        )
        val array = getConsoleArgs.trim.split("\\s+").filter(!_.trim().isEmpty)
        params.getProgramParametersList.addAll(array: _*)
        params.getProgramParametersList.add(scriptPath)
        params.getProgramParametersList.addParametersString(scriptArgs)
        params
      }
    }

    val consoleBuilder = TextConsoleBuilderFactory.getInstance.createBuilder(getProject)
    consoleBuilder.addFilter(getFilter(script))
    state.setConsoleBuilder(consoleBuilder)
    state
  }

  def getModule: Module = {
    var module: Module = null
    try {
      val file: VirtualFile = VcsUtil.getVirtualFile(scriptPath)
      module = ModuleUtilCore.findModuleForFile(file, getProject)
    }
    catch {
      case _: Exception =>
    }
    if (module == null) module = getConfigurationModule.getModule
    module
  }

  def getValidModules: java.util.List[Module] = getProject.modulesWithScala.asJava

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaScriptRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "path", getScriptPath)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "consoleargs", getConsoleArgs)
    JDOMExternalizer.write(element, "params", getScriptArgs)
    JDOMExternalizer.write(element, "workingDirectory", workingDirectory)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    scriptPath = JDOMExternalizer.readString(element, "path")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    scriptArgs = JDOMExternalizer.readString(element, "params")
    consoleArgs = JDOMExternalizer.readString(element, "consoleargs")
    val pp = JDOMExternalizer.readString(element, "workingDirectory")
    if (pp != null) workingDirectory = pp
  }

  private def getFilter(file: VirtualFile): Filter = {
    import com.intellij.execution.filters.Filter._
    (line: String, entireLength: Int) => {
      val start = entireLength - line.length
      var end = entireLength - line.length
      if (line.startsWith("(fragment of ")) {
        try {
          var cache = line.replaceFirst("[(][f][r][a][g][m][e][n][t][ ][o][f][ ]", "")
          cache = cache.replaceFirst("[^)]*[)][:]", "")
          val lineNumber = Integer.parseInt(cache.substring(0, cache.indexOf(":")))
          cache = cache.replaceFirst("[^:]", "")
          end += line.length - cache.length
          val hyperlink = new OpenFileHyperlinkInfo(getProject, file, lineNumber - 1)
          new Result(start, end, hyperlink)
        }
        catch {
          case _: Exception => null
        }
      } else null
    }
  }

  def getRefactoringElementListener(element: PsiElement): RefactoringElementListener = element match {
    case _: ScalaFile => new RefactoringElementAdapter {
      def elementRenamedOrMoved(newElement: PsiElement): Unit = {
        newElement match {
          case f: ScalaFile =>
            val newPath = f.getVirtualFile.getPath
            setScriptPath(newPath)
          case _ =>
        }
      }

      //todo this method does not called when undo of moving action executed
      def undoElementMovedOrRenamed(newElement: PsiElement, oldQualifiedName: String) {
        setScriptPath(oldQualifiedName)
      }
    }
    case _ => RefactoringElementListener.DEAF
  }
}
