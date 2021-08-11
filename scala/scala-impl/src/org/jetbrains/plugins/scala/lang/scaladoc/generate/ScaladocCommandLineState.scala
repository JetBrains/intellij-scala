package org.jetbrains.plugins.scala
package lang.scaladoc.generate

import java.io.{File, FileOutputStream, IOException, PrintStream}
import java.util.regex.Pattern

import com.intellij.analysis.AnalysisScope
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations._
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.openapi.projectRoots.{JdkUtil, Sdk}
import com.intellij.openapi.roots._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project._

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.util.Using

class ScaladocCommandLineState(env: ExecutionEnvironment, project: Project)
  extends JavaCommandLineState(env) {

  setConsoleBuilder(TextConsoleBuilderFactory.getInstance.createBuilder(project))

  private val MAIN_CLASS = "scala.tools.nsc.ScalaDoc"
  private val classpathDelimeter = File.pathSeparator
  private var outputDir: String = ""
  private var showInBrowser: Boolean = false
  private var additionalScaladocFlags: String = ""
  private var scope: AnalysisScope = _
  private var verbose: Boolean = false
  private var docTitle: String = ""
  private var maxHeapSize: String = ""

  def setAdditionalScaladocFlags(flags: String): Unit =
    additionalScaladocFlags = flags

  def setScope(scope: AnalysisScope): Unit =
    this.scope = scope

  def setVerbose(flag: Boolean): Unit =
    verbose = flag

  def setDocTitle(title: String): Unit =
    docTitle = title

  def setMaxHeapSize(size: String): Unit =
    maxHeapSize = size

  def setShowInBrowser(b: Boolean): Unit =
    showInBrowser = b

  def setOutputDir(dir: String): Unit =
    outputDir = dir

  override protected def startProcess: OSProcessHandler = {
    val handler: OSProcessHandler = JavaCommandLineStateUtil.startProcess(createCommandLine)
    if (showInBrowser) {
      handler.addProcessListener(new ProcessAdapter {
        override def processTerminated(event: ProcessEvent): Unit = {
          val url: File = new File(outputDir, "index.html")
          if (url.exists && event.getExitCode == 0) {
            BrowserUtil.browse(url.getPath)
          }
        }
      })
    }
    handler
  }

  private def visitAll(file: VirtualFile, scope: AnalysisScope): List[VirtualFile] = {
    val acc = mutable.ListBuffer[VirtualFile]()

    def visitInner(
      file: VirtualFile,
      scope: AnalysisScope
    ): Unit =
      if (file.isDirectory) {
        for (c <- file.getChildren)
          visitInner(c, scope)
      }
      else if (file.getExtension == "scala" && file.isValid && scope.contains(file)) {
        PsiManager.getInstance(project).findFile(file) match {
          case f: ScalaFile if !f.isScriptFile => acc += file
          case _ => // do nothing
        }
      }

    visitInner(file, scope)
    acc.toList
  }

  private def processAdditionalParams(params: String) = {
    val paramTokens = splitParams(params)
    val result = mutable.ListBuffer.empty[String]

    paramTokens.foldLeft(false) {
      case (true, _) =>
        false
      case (_, param: String) if ScaladocCommandLineState.generatedParamsWithArgs.contains(param) =>
        true
      case (_, param: String) =>
        if (!ScaladocCommandLineState.generatedParamsWithoutArgs.contains(param))
          result += param
        false
    }

    result
  }

  private def splitParams(params: String): List[String] = {
    val result = mutable.ListBuffer.empty[String]
    val acc = new StringBuilder("")

    (params + " ").foldLeft(false) { case (inQuotes, char) =>
      char match {
        case ' '  =>
          if (inQuotes) {
            acc.append(' ')
          } else {
            result += acc.toString
            acc.clear()
          }
          inQuotes
        case '\"' =>
          !inQuotes
        case d =>
          acc.append(d)
          inQuotes
      }
    }

    result.result()
  }

  override def createJavaParameters(): JavaParameters = {
    val jp = new JavaParameters

    val jdk: Sdk = PathUtilEx.getAnyJdk(project)
    assert(jdk != null, "JDK IS NULL")

    // NOTE: do not accidentally add all-project classes scope (e.g. using JDK_AND_CLASSES):
    // it adds jars from sbt-build module which contains scala library with version different from users scala version
    // which can leads to runtime exceptions
    jp.configureByProject(project, JavaParameters.JDK_ONLY, jdk)
    jp.setWorkingDirectory(project.baseDir.getPath)
    val scalaModule = project.anyScalaModule.getOrElse {
      throw new ExecutionException("No modules with Scala SDK are configured")
    }
    jp.getClassPath.addScalaClassPath(scalaModule)
    jp.setCharset(null)
    jp.setMainClass(MAIN_CLASS)

    val vmParamList = jp.getVMParametersList
    if (maxHeapSize.length > 0) {
      vmParamList.add(s"-Xmx${maxHeapSize}m")
      vmParamList.add(s"-Xmx${maxHeapSize}m")
    }

    val paramList = jp.getProgramParametersList

    val paramListSimple = mutable.ListBuffer.empty[String]

    val modules = ModuleManager.getInstance(project).getModules

    val sourcePath = OrderEnumerator.orderEntries(project).withoutLibraries().withoutSdk().getAllSourceRoots
    val documentableFilesList = mutable.ListBuffer.empty[String]
    val allModules = mutable.HashSet(modules.toSeq: _*)
    val modulesNeeded = mutable.HashSet.empty[Module]

    def filterModulesList(files: VirtualFile*): Unit = {
      modulesNeeded ++= allModules.filter(m => files.exists(f => m.getModuleScope.contains(f)))
      allModules --= modulesNeeded
    }
    
    def collectCPSources(
      target: OrderEnumerator,
      classesCollector: collection.mutable.HashSet[String],
      sourcesCollector: collection.mutable.HashSet[String]
    ): Unit = {
      Set(classesCollector -> target.classes, sourcesCollector -> target.sources)
        .foreach { case (collector, enumerator) =>
          val roots = enumerator.withoutSelfModuleOutput().getRoots
          val strings = roots.map { virtualFile =>
            virtualFile.getPath.replaceAll(Pattern.quote(".") + "(\\S{2,6})" + Pattern.quote("!/"), ".$1/")
          }
          collector ++= strings
        }
    }

    val classpathWithFacet = mutable.ListBuffer.empty[String]
    val sourcepathWithFacet = mutable.ListBuffer.empty[String]

    def filterNeededModuleSources(): Unit = {
      val allEntries = mutable.HashSet[String]()
      val allSourceEntries = mutable.HashSet[String]()

      if (modulesNeeded.nonEmpty) {
        for (module <- modulesNeeded) {
          collectCPSources(OrderEnumerator.orderEntries(module), allEntries, allSourceEntries)
        }
      } else {
        collectCPSources(OrderEnumerator.orderEntries(project), allEntries, allSourceEntries)
      }
      allEntries.foreach(classpathWithFacet.append(_))
      allSourceEntries.foreach(sourcepathWithFacet.append(_))
    }

    var needFilter = false

    scope.getScopeType match {
      case AnalysisScope.PROJECT =>
        modulesNeeded ++= allModules
      case AnalysisScope.MODULE =>
        val moduleOpt = modules.find(scope.containsModule)
        modulesNeeded ++= moduleOpt
      case AnalysisScope.MODULES =>
        val modulesFromScope = modules.filter(scope.containsModule)
        modulesNeeded ++= modulesFromScope
      case _ =>
        needFilter = true
    }

    for (className <- sourcePath) {
      val children = className.getChildren

      for (c <- children) {
        val documentableFiles = visitAll(c, scope)
        if (needFilter) {
          filterModulesList(documentableFiles: _*)
        }

        for (docFile <- documentableFiles) {
          documentableFilesList += docFile.getPath
        }
      }
    }

    filterNeededModuleSources()

    paramListSimple += "-d"
    paramListSimple += outputDir

    paramListSimple += "-classpath"
    paramListSimple += classpathWithFacet.mkString(classpathDelimeter)

    paramListSimple += "-sourcepath"
    paramListSimple += sourcepathWithFacet.mkString(classpathDelimeter)

    if (verbose) {
      paramListSimple += "-verbose"
    }

    paramListSimple += "-doc-title"
    paramListSimple += docTitle

    if (additionalScaladocFlags.nonEmpty) {
      paramListSimple ++= processAdditionalParams(additionalScaladocFlags)
    }

    paramListSimple ++= documentableFilesList

    if (JdkUtil.useDynamicClasspath(project)) {
      try {
        val tempParamsFile: File = File.createTempFile("scaladocfileargs", ".tmp")
        Using.resource(new PrintStream(new FileOutputStream(tempParamsFile))) { pw =>
          paramListSimple.map(escapeParam).foreach(pw.println)
        }
        paramList.add("@" + tempParamsFile.getAbsolutePath)
      }
      catch {
        case e: IOException => throw new ExecutionException("I/O Error", e)
      }
    } else {
      paramList.addAll(paramListSimple.asJava)
    }

    jp
  }

  private def escapeParam(param: String) =
    if (param.contains(" ") && !(param.startsWith("\"") && param.endsWith("\"")))
      "\"" + param + "\""
    else
      param
}

object ScaladocCommandLineState {
  val generatedParamsWithArgs = List("-d", "-doc-title", "-classpath")
  val generatedParamsWithoutArgs = List("-verbose")
}
