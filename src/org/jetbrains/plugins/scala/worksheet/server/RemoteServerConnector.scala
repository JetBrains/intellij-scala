package org.jetbrains.plugins.scala
package worksheet.server


import java.io._
import java.net._
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import com.intellij.util.{Base64Converter, PathUtil}
import org.jetbrains.plugins.scala.compiler.ScalaApplicationSettings
import org.jetbrains.jps.incremental.scala.remote._
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala
import org.jetbrains.plugin.scala.compiler.IncrementalType
import com.intellij.openapi.roots.{ModuleRootManager, OrderEnumerator}
import org.jetbrains.plugins.scala.config.ScalaFacet
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.SbtData
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.{OuterCompilerInterface, DummyClient}
import com.intellij.openapi.progress.{ProgressManager, ProgressIndicator}
import com.intellij.openapi.project.Project
import com.intellij.compiler.CompilerMessageImpl
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import com.intellij.openapi.compiler.{CompilerPaths, CompilerMessageCategory}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.compiler.progress.CompilerTask
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import com.intellij.openapi.application.ApplicationManager

/**
  * User: Dmitry Naydanov
 * Date: 1/28/14
 */
class RemoteServerConnector(module: Module, worksheet: File, output: File) {
  private val libRoot = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      new File("../out/cardea/artifacts/Scala/lib") else new File(PathUtil.getJarPathForClass(getClass)).getParentFile
  }
  
  private val libCanonicalPath = PathUtil.getCanonicalPath(libRoot.getPath)
  
  private val sbtData = SbtData.from(
    new URLClassLoader(Array(new URL("jar:file:" + (if (libCanonicalPath startsWith "/") "" else "/" ) + libCanonicalPath + "/jps/sbt-interface.jar!/")), getClass.getClassLoader),
    new File(libRoot, "jps"),
    new File(System.getProperty("user.home"), ".idea-build"),
    System.getProperty("java.class.version") 
  ) match {
    case Left(msg) => throw new IllegalArgumentException(msg)
    case Right(data) => data
  }
  
  private val scalaParameters = facet.compilerParameters
  
  private val javaParameters = Array.empty[String]


  /**
   *     Seq(
      fileToPath(sbtData.interfaceJar),
      fileToPath(sbtData.sourceJar),
      fileToPath(sbtData.interfacesHome),
      sbtData.javaClassVersion,
      optionToString(compilerJarPaths),
      optionToString(javaHomePath),
      filesToPaths(compilationData.sources),
      filesToPaths(compilationData.classpath),
      fileToPath(compilationData.output),
      sequenceToString(compilationData.scalaOptions),
      sequenceToString(compilationData.javaOptions),
      compilationData.order.toString,
      fileToPath(compilationData.cacheFile),
      filesToPaths(outputs),
      filesToPaths(caches),
      incrementalType.name,
      <s>worksheetFilePath,</s> - deleted    
      filesToPaths(sourceRoots),
      filesToPaths(outputDirs),
      worksheetClassName
    )
   */
  def compileAndRun(callback: Runnable, originalFile: VirtualFile, consumer: OuterCompilerInterface, 
                    worksheetClassName: String, runType: WorksheetMakeType): ExitCode = {
    implicit def file2path(file: File) = FileUtil.toCanonicalPath(file.getAbsolutePath)
    implicit def option2string(opt: Option[String]) = opt getOrElse ""
    implicit def files2paths(files: Iterable[File]) = files map file2path mkString "\n"
    implicit def array2string(arr: Array[String]) = arr mkString "\n"

    val project = module.getProject
    val worksheetHook = WorksheetFileHook.instance(project)

    val client = new DummyClient(callback, project, originalFile, consumer)
    val compilerJar = facetCompiler
    val libraryJar = facetLibrary

    val extraJar = facetFiles filter {
      case a => a != compilerJar && a != libraryJar 
    }
    val runnersJar = new File(libCanonicalPath, "scala-plugin-runners.jar")
    val compilerSettingsJar = new File(libCanonicalPath, "compiler-settings.jar")
    
    val additionalCp = facetFiles :+ runnersJar :+ compilerSettingsJar :+ output 
    
    val worksheetArgs = 
      if (runType != OutOfProcessServer) Array(worksheetClassName, runnersJar.getAbsolutePath, output.getAbsolutePath) ++ outputDirs
      else Array.empty[String]

    val arguments = Seq[String](
      sbtData.interfaceJar,
      sbtData.sourceJar,
      sbtData.interfacesHome, 
      sbtData.javaClassVersion, 
      Seq(libraryJar, compilerJar) ++ extraJar, 
      findJdk, 
      worksheet,
      (assemblyClasspath().toSeq map (f => new File(f.getCanonicalPath stripSuffix "!" stripSuffix "!/"))) ++ additionalCp, 
      output, 
      scalaParameters,
      javaParameters, 
      settings.COMPILE_ORDER.toString, 
      "", //cache file
      "", 
      "", 
      IncrementalType.IDEA.name(),
      worksheet.getParentFile, 
      output, 
      worksheetArgs
    )

    try {
      val worksheetProcess = runType match {
        case InProcessServer | OutOfProcessServer =>
           new WorksheetRemoteServerRunner(project).run(arguments, client)
        case NonServer =>
          val eventClient = new ClientEventProcessor(client)
          
          val encodedArgs = arguments map {
            case "" => Base64Converter.encode("#STUB#" getBytes "UTF-8")
            case s => Base64Converter.encode(s getBytes "UTF-8")
          }

          new WorksheetNonServerRunner(project).run(encodedArgs, (text: String) => {
            val event = Event.fromBytes(Base64Converter.decode(text.getBytes("UTF-8")))
            eventClient.process(event)
          })
      }
      
      if (worksheetProcess == null) return ExitCode.ABORT
      
      worksheetHook.initTopComponent(originalFile, run = false, Some(worksheetProcess))
      worksheetProcess.addTerminationCallback({worksheetHook.initTopComponent(originalFile, run = true)})
      worksheetProcess.run()
      
      ExitCode.OK
    } catch {
      case e: SocketException =>
        ExitCode.OK // someone has stopped the server
    } 
  }

  
  private def configurationError(message: String) = throw new IllegalArgumentException(message)

  private def assemblyClasspath() = OrderEnumerator.orderEntries(module).compileOnly().getClassesRoots
  
  private def outputDirs = (ModuleRootManager.getInstance(module).getDependencies :+ module).map {
    case m => CompilerPaths.getModuleOutputPath(m, false)
  } 

  private def settings = ScalaApplicationSettings.getInstance()

  private def facet =
    ScalaFacet.findIn(module) getOrElse configurationError("No Scala facet configured for module: " + module.getName)
  
  private def facetCompiler = 
    facet.compiler flatMap (_.jar) getOrElse configurationError("No compiler jar for Scala Facet in module: " + module.getName)
  
  private def facetLibrary = 
    facet.compiler.flatMap {
      case c => c.files find {
        case file if file.getName contains "library" => true
        case _ => false
      }
    } getOrElse configurationError("No library jar for Scala Facet in module: " + module.getName)
  
  private def facetFiles = facet.compiler flatMap {
    case compiler => compiler.jar map {
      case j => compiler.files
    }
  } getOrElse Seq.empty
  
  private def findJdk = scala.compiler.findJdkByName(settings.COMPILE_SERVER_SDK) match {
    case Right(jdk) => jdk.executable
    case Left(msg) => 
      configurationError(s"Cannot find jdk ${settings.COMPILE_SERVER_SDK} for compile server, underlying message: $msg" )
  }
}

object RemoteServerConnector {
  class DummyClient(callback: Runnable, project: Project, worksheet: VirtualFile, consumer: OuterCompilerInterface) extends Client {
    private val length = WorksheetSourceProcessor.END_GENERATED_MARKER.length
    
    private var hasErrors = false
    
    override def isCanceled: Boolean = false
    override def deleted(module: File) {}
    override def processed(source: File) {}
    override def generated(source: File, module: File, name: String) {}
    override def debug(text: String) {}

    override def progress(text: String, done: Option[Float]) {
      consumer.progress(text, done)
    }
    override def trace(exception: Throwable) {
      throw new RuntimeException(exception)
    }
    override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
      val lines = text split "\n"
      val linesLength = lines.length

      val differ = if (linesLength > 2) {
        val i = lines(linesLength - 2) indexOf WorksheetSourceProcessor.END_GENERATED_MARKER
        if (i > -1) i + length else 0
      } else 0
      
      val finalText = if (differ == 0) text else {
        val buffer = new StringBuilder
        
        for (j <- 0 until (linesLength - 2)) buffer append lines(j) append "\n"
        
        buffer append lines(linesLength - 2).substring(differ) append "\n" append lines(linesLength - 1).substring(differ) append "\n"
        buffer.toString()
      }
      
      val line1 = line.map(i => i - 3).map(_.toInt)
      val column1 = column.map(_ + 1 - differ).map(_.toInt)

      val category = kind match {
        case BuildMessage.Kind.INFO => CompilerMessageCategory.INFORMATION
        case BuildMessage.Kind.ERROR => 
          hasErrors = true
          CompilerMessageCategory.ERROR
        case BuildMessage.Kind.PROGRESS => CompilerMessageCategory.STATISTICS
        case BuildMessage.Kind.WARNING => CompilerMessageCategory.WARNING
      }
      
      consumer.message(
        new CompilerMessageImpl(project, category, finalText, worksheet, line1 getOrElse -1, column1 getOrElse -1, null)
      )
    }

    override def compilationEnd() {
      if (!hasErrors) callback.run()
    }

    override def worksheetOutput(text: String) {
      consumer.worksheetOutput(text)
    }
  }
  
  trait OuterCompilerInterface {
    def message(message: CompilerMessageImpl)
    def progress(text: String, done: Option[Float])
    
    def worksheetOutput(text: String)
  }
  
  class CompilerInterfaceImpl(task: CompilerTask, worksheetPrinter: WorksheetEditorPrinter, indicator: Option[ProgressIndicator]) extends OuterCompilerInterface {
    override def progress(text: String, done: Option[Float]) {
      val taskIndicator = ProgressManager.getInstance().getProgressIndicator
      
      if (taskIndicator != null) {
        taskIndicator setText text
        done map (d => taskIndicator.setFraction(d.toDouble))
      }
    }

    override def message(message: CompilerMessageImpl) {
      task addMessage message
    }

    override def worksheetOutput(text: String) {
      worksheetPrinter.processLine(text)
    }
  }
}
