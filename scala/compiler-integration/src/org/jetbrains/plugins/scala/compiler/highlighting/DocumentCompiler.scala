package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.{CompilerManager, CompilerPaths}
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.scala.remote.{CommandIds, SourceScope}
import org.jetbrains.jps.incremental.scala.{Client, DelegateClient}
import org.jetbrains.plugins.scala.compiler.{RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel, VirtualFileExt}

import java.io.File
import java.nio.file.{Files, Path}

@Service(Array(Service.Level.PROJECT))
private final class DocumentCompiler(project: Project) {

  private val workingDirectory: Path = {
    var compilerDir = CompilerManager.getInstance(project).getJavacCompilerWorkingDir
    if (compilerDir eq null) {
      // This shouldn't happen, as the implementation of `CompilerManagerImpl#getJavacCompilerWorkingDir`
      // does not return a nullable file, but just in case, this is the same directory.
      compilerDir = BuildManager.getInstance().getProjectSystemDirectory(project)
    }
    compilerDir = compilerDir.toPath.resolve("document-compiler").toFile
    if (!compilerDir.exists()) {
      compilerDir.mkdirs()
    }
    compilerDir.toPath
  }

  def compile(
    module: Module,
    sourceScope: SourceScope,
    document: Document,
    virtualFile: VirtualFile,
    client: Client
  ): Unit = {
    compileDocumentContent(
      originalSourceFile = virtualFile.toFile,
      content = document.textWithConvertedSeparators(virtualFile),
      module = module,
      sourceScope = sourceScope,
      client = client
    )
  }

  private def compileDocumentContent(originalSourceFile: File,
                                     content: String,
                                     module: Module,
                                     sourceScope: SourceScope,
                                     client: Client): Unit = {
    val tempSourceFile = workingDirectory.resolve("tempSourceFile").toFile
    Files.writeString(tempSourceFile.toPath, content)
    val connector =
      try new RemoteServerConnector(tempSourceFile, module, sourceScope)
      catch {
        case t: Throwable =>
          // Remove the temporary source file if creating the connector failed.
          FileUtil.delete(tempSourceFile)
          throw t
      }

    try connector.compile(originalSourceFile, client)
    finally {
      if (connector.requiresCleanup) {
        val files = workingDirectory.toFile.listFiles()
        if (files ne null) {
          files.foreach(FileUtil.delete)
        }
      } else {
        FileUtil.delete(tempSourceFile)
      }
    }
  }

  private class RemoteServerConnector(tempSourceFile: File, module: Module, sourceScope: SourceScope)
    extends RemoteServerConnectorBase(module, Some(Seq(tempSourceFile)), workingDirectory.toFile) {

    var requiresCleanup: Boolean = false

    override protected def scalaParameters: Seq[String] = {
      var scalacOptions = CompilerOptions.scalacOptions(module)
      if (!CompilerOptions.containsStopAfter(scalacOptions)) {
        val stopAfter = module.scalaLanguageLevel match {
          case Some(ScalaLanguageLevel.Scala_2_10) => Some("-Ystop-after:dce")
          case Some(ScalaLanguageLevel.Scala_2_11) |
               Some(ScalaLanguageLevel.Scala_2_12) |
               Some(ScalaLanguageLevel.Scala_2_13) => Some("-Ystop-after:delambdafy")
          case Some(languageLevel) if languageLevel.isScala3 => Some("-Ystop-after:repeatableAnnotations")
          case _ =>
            // .class files will be produced, they should be cleaned up
            requiresCleanup = true
            None
        }
        scalacOptions ++= stopAfter
      } else {
        // .class/tasty files might be produced, they should be cleaned up if they exist
        requiresCleanup = true
      }
      if (module.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_3_3)) {
        if (!CompilerOptions.containsUnusedImports(scalacOptions)) {
          scalacOptions = scalacOptions :+ "-Wunused:imports"
        }
      }
      scalacOptions
    }

    override protected def assemblyRuntimeClasspath(): Seq[File] = {
      val fromSuper = super.assemblyRuntimeClasspath()
      val forTestClasses = sourceScope match {
        case SourceScope.Production => false
        case SourceScope.Test => true
      }

      val outputDir =
        Option(CompilerPaths.getModuleOutputPath(module, forTestClasses))
          .map(Path.of(_).toFile)
      (fromSuper ++ outputDir).distinct
    }

    def compile(originalSourceFile: File, client: Client): Unit = {
      val fixedClient = new DelegateClient(client) {
        override def message(msg: Client.ClientMsg): Unit = {
          /**
           * NOTE: some compiler errors can be with empty `source`<br>
           * Example: `bad option '-g:vars' was ignored`<br>
           * We do not want to loose such message.
           * We rely that they will be reported in the beginning of the file<br>
           * see [[org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlightersService.toHighlightInfo]]
           * (we assume that `from` and `to` are also empty for such files)
           */
          val fixedSource = Some(originalSourceFile) //msg.source.map(_ => originalSourceFile)
          val fixedMsg = msg.copy(source = fixedSource)
          client.message(fixedMsg)
        }

        override def compilationEnd(sources: Set[File]): Unit = {
          val fixedSources = Set(originalSourceFile)
          client.compilationEnd(fixedSources)
        }
      }
      new RemoteServerRunner()
        .buildProcess(CommandIds.Compile, arguments.asStrings, fixedClient)
        .runSync()
    }
  }
}

private object DocumentCompiler {
  def get(project: Project): DocumentCompiler =
    project.getService(classOf[DocumentCompiler])
}