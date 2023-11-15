package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilerPaths
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
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.CollectionHasAsScala

@Service(Array(Service.Level.PROJECT))
private final class DocumentCompiler(project: Project) extends Disposable {

  private val outputDirectories = new ConcurrentHashMap[Module, File]

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

  def clearOutputDirectories(): Unit = {
    outputDirectories.values().asScala.flatMap(dir => Option(dir.listFiles())).flatten.foreach(FileUtil.delete)
  }

  private def removeOutputDirectories(): Unit = {
    outputDirectories.values().asScala.foreach(FileUtil.delete)
  }

  override def dispose(): Unit = {
    clearOutputDirectories()
    removeOutputDirectories()
    outputDirectories.clear()
  }

  private def compileDocumentContent(originalSourceFile: File,
                                     content: String,
                                     module: Module,
                                     sourceScope: SourceScope,
                                     client: Client): Unit = {
    val tempSourceFile = FileUtil.createTempFile("tempSourceFile", null, false)
    val outputDir = outputDirectories.computeIfAbsent(module, { _: Module =>
      val prefix = s"${module.getProject.getName}-${module.getName}-target"
      FileUtil.createTempDirectory(prefix, null, true)
    })
    try {
      Files.writeString(tempSourceFile.toPath, content)
      new RemoteServerConnector(tempSourceFile, module, sourceScope, outputDir).compile(originalSourceFile, client)
    } finally {
      FileUtil.delete(tempSourceFile)
    }
  }

  private class RemoteServerConnector(tempSourceFile: File,
                                      module: Module,
                                      sourceScope: SourceScope,
                                      outputDir: File)
    extends RemoteServerConnectorBase(module, Some(Seq(tempSourceFile)), outputDir) {

    override protected def scalaParameters: Seq[String] = {
      val scalacOptions = CompilerOptions.scalacOptions(module)
      if (module.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_3_3)) {
        if (CompilerOptions.containsUnusedImports(scalacOptions)) scalacOptions
        else scalacOptions :+ "-Wunused:imports"
      }
      else scalacOptions
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
           * see [[org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighters.toHighlightInfo]]
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