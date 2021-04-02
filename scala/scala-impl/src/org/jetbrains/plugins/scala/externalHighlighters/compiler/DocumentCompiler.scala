package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.jps.incremental.scala.{Client, DelegateClient}
import org.jetbrains.plugins.scala.compiler.{RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.project.VirtualFileExt

import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.CollectionHasAsScala

@Service
final class DocumentCompiler(project: Project)
  extends Disposable {

  private val outputDirectories = new ConcurrentHashMap[Module, File]

  def compile(document: Document, client: Client): Unit =
    for {
      virtualFile <- document.virtualFile
      module <- Option(ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile))
    } compileDocumentContent(
      originalSourceFile = virtualFile.toFile,
      content = document.textWithConvertedSeparators(virtualFile),
      module = module,
      client = client
    )
    
  def clearOutputDirectories(): Unit =
    for {
      outputDir <- outputDirectories.values.asScala
      file <- outputDir.listFiles()
    } FileUtil.delete(file)

  override def dispose(): Unit =
    outputDirectories.clear()

  private def compileDocumentContent(originalSourceFile: File,
                                     content: String,
                                     module: Module,
                                     client: Client): Unit = {
    val tempSourceFile = FileUtil.createTempFile("tempSourceFile", null, false)
    val outputDir = outputDirectories.computeIfAbsent(module, { _: Module =>
      val prefix = s"${module.getProject.getName}-${module.getName}-target"
      FileUtil.createTempDirectory(prefix, null, true)
    })
    try {
      Files.writeString(tempSourceFile.toPath, content)
      new RemoteServerConnector(tempSourceFile, module, outputDir).compile(originalSourceFile, client)
    } finally {
      FileUtil.delete(tempSourceFile)
    }
  }

  private class RemoteServerConnector(tempSourceFile: File,
                                      module: Module,
                                      outputDir: File)
    extends RemoteServerConnectorBase(module, Some(Seq(tempSourceFile)), outputDir) {

    def compile(originalSourceFile: File, client: Client): Unit = {
      val fixedClient = new DelegateClient(client) {
        override def message(msg: Client.ClientMsg): Unit = {
          val fixedSource = msg.source.map(_ => originalSourceFile)
          val fixedMsg = msg.copy(source = fixedSource)
          client.message(fixedMsg)
        }

        override def compilationEnd(sources: Set[File]): Unit = {
          val fixedSources = Set(originalSourceFile)
          client.compilationEnd(fixedSources)
        }
      }
      new RemoteServerRunner(module.getProject)
        .buildProcess(CommandIds.Compile, arguments.asStrings, fixedClient)
        .runSync()
    }
  }
}

object DocumentCompiler {

  def get(project: Project): DocumentCompiler =
    ServiceManager.getService(project, classOf[DocumentCompiler])

  def outputDirectoryFor(module: Module): Option[File] = {
    val project = module.getProject
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
      Option(get(project).outputDirectories.get(module))
    else
      None
  }
}