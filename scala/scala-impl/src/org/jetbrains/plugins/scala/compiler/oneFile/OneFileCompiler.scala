package org.jetbrains.plugins.scala.compiler.oneFile

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileWithId
import org.apache.commons.io.IOUtils
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.using

trait OneFileCompiler {

  def compile(project: Project, source: Document): Seq[Client.ClientMsg]
}

object OneFileCompiler
  extends OneFileCompiler {

  override def compile(project: Project, source: Document): Seq[Client.ClientMsg] = {
    val result = for {
      projectFileIndex <- Option(ProjectFileIndex.SERVICE.getInstance(project))
      if !DumbService.getInstance(project).isDumb
      module <- getModuleOf(source, projectFileIndex)
      sourceFileCopy = copyDocumentContentToFile(source)
      connector = new RemoteServerConnector(
        module = module,
        sourceFile = sourceFileCopy,
        outputDir = None
      )
    } yield connector.compile()
    result.getOrElse(throw new RuntimeException("Not compiled cuz no indexes"))
  }

  private def getModuleOf(source: Document,
                          projectFileIndex: ProjectFileIndex): Option[Module] =
    for {
      virtualFile <- Option(FileDocumentManager.getInstance.getFile(source))
      if virtualFile.isInstanceOf[VirtualFileWithId]
      module <- Option(projectFileIndex.getModuleForFile(virtualFile))
    } yield module

  private def copyDocumentContentToFile(document: Document): File = {
    val file = FileUtil.createTempFile("tmp", "", true)
    val content = document.getImmutableCharSequence
    using(IOUtils.toInputStream(content, Charset)) { inputStream =>
      using(new FileOutputStream(file)) { outputStream =>
        FileUtil.copy(inputStream, outputStream)
      }
    }
    file
  }

  private final val Charset = StandardCharsets.UTF_8
}
