package org.jetbrains.plugins.scala
package script

import com.intellij.execution.configurations._
import com.intellij.execution.filters._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{ExecutionException, Executor}
import com.intellij.openapi.module.{ModuleUtilCore, Module}
import com.intellij.openapi.options.SettingsEditor
import lang.psi.api.ScalaFile
import com.intellij.vcsUtil.VcsUtil
import org.jdom.Element
import collection.JavaConversions._
import compiler.ScalacSettings
import config.{Libraries, CompilerLibraryData, ScalaFacet}
import com.intellij.refactoring.listeners.{RefactoringElementAdapter, RefactoringElementListener}

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

  def getScriptPath = scriptPath
  def getScriptArgs = scriptArgs
  def getJavaOptions = javaOptions
  def getConsoleArgs = consoleArgs
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
        case f: ScalaFile if f.isScriptFile() && !f.isWorksheetFile =>
        case _ => fileNotFoundError()
      }
    }
    catch {
      case e: Exception => fileNotFoundError()
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
//        params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
//        params.getVMParametersList.add(SCALA_HOME  + scalaSdkPath)
        params.getVMParametersList.add(CLASSPATH)
        params.getVMParametersList.add(EMACS)

        params.setMainClass(MAIN_CLASS)
        params.getProgramParametersList.add("-nocompdaemon") //todo: seems to be a bug in scala compiler. Ticket #1498
        params.getProgramParametersList.add("-classpath")
        params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)
        params.getProgramParametersList.add(params.getClassPath.getPathsString)
        ScalaFacet.findIn(module).foreach {
          case facet =>
            val files =
              if (facet.fsc) {
                val settings = ScalacSettings.getInstance(getProject)
                val lib: Option[CompilerLibraryData] = Libraries.findBy(settings.COMPILER_LIBRARY_NAME,
                  settings.COMPILER_LIBRARY_LEVEL, getProject)
                lib match {
                  case Some(libr) => libr.files
                  case _ => facet.files
                }
              } else facet.files
            files.foreach(params.getClassPath.add)
        }
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
      case e: Exception =>
    }
    if (module == null) module = getConfigurationModule.getModule
    module
  }

  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaScriptRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    writeModule(element)
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
    import Filter._
    new Filter {
      def applyFilter(line: String, entireLength: Int): Result = {
        val start = entireLength - line.length
        var end = entireLength - line.length
        if (line.startsWith("(fragment of ")) {
          try {
            var cache = line.replaceFirst("[(][f][r][a][g][m][e][n][t][ ][o][f][ ]", "")
            cache = cache.replaceFirst("[^)]*[)][:]", "")
            val lineNumber = Integer.parseInt(cache.substring(0, cache.indexOf(":")))
            cache = cache.replaceFirst("[^:]", "")
            end += line.length - cache.length
            val hyperlink = new OpenFileHyperlinkInfo(getProject, file, lineNumber-1)
            new Result(start, end, hyperlink)
          }
          catch {
            case _: Exception => return null
          }
        } else null
      }
    }
  }

  def getRefactoringElementListener(element: PsiElement): RefactoringElementListener = element match {
    case file: ScalaFile => new RefactoringElementAdapter {
      def elementRenamedOrMoved(newElement: PsiElement) = {
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