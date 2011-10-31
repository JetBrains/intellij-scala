package org.jetbrains.plugins.scala.lang.scaladoc.generate

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.configurations._
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.ide.BrowserUtil
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.projectRoots.{JdkUtil, Sdk}
import java.io.{FileOutputStream, IOException, PrintStream, File}
import com.intellij.execution.ExecutionException
import org.jetbrains.plugins.scala.config.ScalaFacet
import scala.collection.mutable.{ListBuffer, MutableList}

/**
 * User: Dmitry Naidanov
 * Date: 12.10.11
 */

class ScaladocCommandLineState(env: ExecutionEnvironment, project: Project)
        extends JavaCommandLineState(env) {
  setConsoleBuilder(TextConsoleBuilderFactory.getInstance.createBuilder(project))
  private val MAIN_CLASS = "scala.tools.nsc.ScalaDoc"
  private var outputDir: String = ""
  private var showInBrowser: Boolean = false
  private var additionalScaladocFlags: String = ""
  private var scope: AnalysisScope = null
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
            BrowserUtil.launchBrowser(url.getPath)
          }
        }
      })
    }
    handler
  }

  private def visitAll(file: VirtualFile, scope: AnalysisScope,
                       acc: MutableList[VirtualFile] = MutableList[VirtualFile]()): MutableList[VirtualFile] = {
    if (file.isDirectory) {
      for (c <- file.getChildren) {
        visitAll(c, scope, acc)
      }
    } else {
      if (file.getExtension == "scala" && scope.contains(file)) {
        acc += file
      }
    }

    acc
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
  

  def createJavaParameters() = {
    import scala.collection.JavaConversions._

    val jp = new JavaParameters
    val jdk: Sdk = PathUtilEx.getAnyJdk(project)
    assert(jdk != null, "JDK IS NULL")
    jp.configureByProject(project, JavaParameters.JDK_AND_CLASSES_AND_TESTS, jdk)
    jp.setWorkingDirectory(project.getBaseDir.getPath)

    val modules = ModuleManager.getInstance(project).getModules
    val facets = ScalaFacet.findIn(modules)
    if (facets.isEmpty) throw new ExecutionException("No facets are configured")
    val facet: ScalaFacet = facets(0)
    val cpWithoutFacet = jp.getClassPath.getPathsString
    jp.getClassPath.addAll(facet.classpath.split(";").toList)
    jp.setCharset(null)
    jp.setMainClass(MAIN_CLASS)

    val vmParamList = jp.getVMParametersList
    if (maxHeapSize.length > 0) {
      vmParamList.add("-Xmx" + maxHeapSize + "m")
    }

    val paramList = jp.getProgramParametersList

    val paramListSimple = scala.collection.mutable.ListBuffer[String]()

    paramListSimple += "-d"
    paramListSimple += outputDir

    paramListSimple += ("-classpath")
    paramListSimple += cpWithoutFacet

    if (verbose) {
      paramListSimple += "-verbose"
    }

    paramListSimple += "-doc-title"
    paramListSimple += docTitle

    if (additionalScaladocFlags.length() > 0) {
      paramListSimple.addAll(processAdditionalParams(additionalScaladocFlags))
    }

    val sourcePath = OrderEnumerator.orderEntries(project).withoutLibraries().withoutSdk().getAllSourceRoots

    for (className <- sourcePath) {
      val children = className.getChildren

      for (c <- children) {
        val documentableFiles = visitAll(c, scope)

        for (docFile <- documentableFiles) {
          paramListSimple += docFile.getPath
        }
      }
    }

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
      paramList.addAll(paramListSimple)
    }

    jp
  }
}

object ScaladocCommandLineState {
  val generatedParamsWithArgs = List("-d", "-doc-title", "-classpath")
  val generatedParamsWithoutArgs = List("-verbose")
}
