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

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * User: Dmitry Naidanov
 * Date: 12.10.11
 */

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

  def setAdditionalScaladocFlags(flags: String) {
    additionalScaladocFlags = flags
  }

  def setScope(scope: AnalysisScope) {
    this.scope = scope
  }

  def setVerbose(flag: Boolean) {
    verbose = flag
  }

  def setDocTitle(title: String) {
    docTitle = title
  }

  def setMaxHeapSize(size: String) {
    maxHeapSize = size
  }

  def setShowInBrowser(b: Boolean) {
    showInBrowser = b
  }

  def setOutputDir(dir: String) {
    outputDir = dir
  }


  override protected def startProcess: OSProcessHandler = {
    val handler: OSProcessHandler = JavaCommandLineStateUtil.startProcess(createCommandLine)
    if (showInBrowser) {
      handler.addProcessListener(new ProcessAdapter {
        override def processTerminated(event: ProcessEvent) {
          val url: File = new File(outputDir, "index.html")
          if (url.exists && event.getExitCode == 0) {
            BrowserUtil.browse(url.getPath)
          }
        }
      })
    }
    handler
  }

  private def visitAll(file: VirtualFile, scope: AnalysisScope,
                       acc: mutable.MutableList[VirtualFile] = mutable.MutableList[VirtualFile]()): List[VirtualFile] = {

    def visitInner(file: VirtualFile, scope: AnalysisScope,
                   acc: mutable.MutableList[VirtualFile] = mutable.MutableList[VirtualFile]()): mutable.MutableList[VirtualFile] = {
      if (file == null) return acc
      if (file.isDirectory) {
        for (c <- file.getChildren) {
          visitInner(c, scope, acc)
        }
      } else {
        if (file.getExtension == "scala" && file.isValid && scope.contains(file)) {
          PsiManager.getInstance(project).findFile(file) match {
            case f: ScalaFile if !f.isScriptFile => acc += file
            case _ => // do nothing
          }
        }
      }

      acc
    }

    val answer = visitInner(file, scope, acc)
    answer.toList
  }

  private def processAdditionalParams(params: String) = {
    val paramTokens = splitParams(params)
    val result = ListBuffer[String]()

    paramTokens.foldLeft(false) {
      case (true, _) => false
      case (_, param: String) if ScaladocCommandLineState.generatedParamsWithArgs.contains(param) => true
      case (_, param: String) =>
        if (!ScaladocCommandLineState.generatedParamsWithoutArgs.contains(param)) result += param
        false
    }

    result
  }

  private def splitParams(params: String): List[String] = {
    val result = ListBuffer[String]()

    (params + " ").foldLeft((false, new StringBuilder(""))) {
      case ((flag, acc), ' ') =>
        if (flag) {
          acc.append(' ')
        } else {
          result += acc.toString
          acc.clear()
        }
        (flag, acc)
      case ((flag, acc), '\"') =>
        (!flag, acc)
      case ((flag, acc), d) =>
        acc.append(d)
        (flag, acc)
    }

    result.result()
  }

  def createJavaParameters(): JavaParameters = {
    val MutableHashSet = collection.mutable.HashSet

    val jp = new JavaParameters
    val jdk: Sdk = PathUtilEx.getAnyJdk(project)
    assert(jdk != null, "JDK IS NULL")
    jp.configureByProject(project, JavaParameters.JDK_AND_CLASSES_AND_TESTS, jdk)
    jp.setWorkingDirectory(project.getBaseDir.getPath)

    val scalaModule = project.anyScalaModule.getOrElse {
      throw new ExecutionException("No modules with Scala SDK are configured")
    }
    val classpathWithFacet = ListBuffer.apply[String]()
    val sourcepathWithFacet = ListBuffer.apply[String]()
    jp.getClassPath.addAllFiles(scalaModule.sdk.compilerClasspath.asJava)
    jp.setCharset(null)
    jp.setMainClass(MAIN_CLASS)

    val vmParamList = jp.getVMParametersList
    if (maxHeapSize.length > 0) {
      vmParamList.add("-Xmx" + maxHeapSize + "m")
    }

    val paramList = jp.getProgramParametersList

    val paramListSimple =  ListBuffer.apply[String]()

    val modules = ModuleManager.getInstance(project).getModules

    val sourcePath = OrderEnumerator.orderEntries(project).withoutLibraries().withoutSdk().getAllSourceRoots
    val documentableFilesList = ListBuffer.apply[String]()
    val allModules = MutableHashSet.apply(modules: _*)
    val modulesNeeded = MutableHashSet.apply[Module]()

    def filterModulesList(files: VirtualFile*) {
      modulesNeeded ++= allModules.filter(m => files.exists(f => m.getModuleScope.contains(f)))
      allModules --= modulesNeeded
    }
    
    def collectCPSources(target: OrderEnumerator, classesCollector: collection.mutable.HashSet[String],
                         sourcesCollector: collection.mutable.HashSet[String]) {
      Set(classesCollector -> target.classes(), sourcesCollector -> target.sources()).foreach {
        entry => entry._1 ++= entry._2.withoutSelfModuleOutput().getRoots.map {
          virtualFile => virtualFile.getPath.replaceAll(Pattern.quote(".") + "(\\S{2,6})" + Pattern.quote("!/"), ".$1/")
        }
      }
    }

    def filterNeededModuleSources() {
      val allEntries = MutableHashSet.apply[String]()
      val allSourceEntries = MutableHashSet.apply[String]()

      if (modulesNeeded.nonEmpty) {
        for (module <- modulesNeeded) {
          collectCPSources(OrderEnumerator.orderEntries(module), allEntries, allSourceEntries)
        }
      } else {
        collectCPSources(OrderEnumerator.orderEntries(project), allEntries, allSourceEntries)
      }
      allEntries.foreach(a => classpathWithFacet.append(a))
      allSourceEntries.foreach(a => sourcepathWithFacet.append(a))
    }

    var needFilter = false

    scope.getScopeType match {
      case AnalysisScope.PROJECT =>
        modulesNeeded ++= allModules
      case AnalysisScope.MODULE =>
        modules.find(scope.containsModule) match {
          case Some(a) => modulesNeeded += a
          case None =>
        }
      case AnalysisScope.MODULES => 
        for (module <- modules) {
          if (scope.containsModule(module)){
            modulesNeeded += module
          }
        }
      case _ => needFilter = true
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
        val pw: PrintStream = new PrintStream(new FileOutputStream(tempParamsFile))

        for (param <- paramListSimple) {
          var paramEsc = param
          if (param.contains(" ") && !(param.startsWith("\"") && param.endsWith("\""))) {
            paramEsc = "\"" + param + "\""
          }

          pw.println(paramEsc)
        }

        pw.close()
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
}

object ScaladocCommandLineState {
  val generatedParamsWithArgs = List("-d", "-doc-title", "-classpath")
  val generatedParamsWithoutArgs = List("-verbose")
}
