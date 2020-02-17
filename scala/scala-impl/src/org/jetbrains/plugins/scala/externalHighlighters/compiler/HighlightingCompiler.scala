package org.jetbrains.plugins.scala.externalHighlighters.compiler

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.io.IOUtils
import org.jetbrains.jps.incremental.scala.using
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.externalHighlighters.RemoteServerConnector
import org.jetbrains.plugins.scala.util.Opt

trait HighlightingCompiler {

  def compile(project: Project, source: Document): Opt[Unit]
}

class HighlightingCompilerImpl
  extends HighlightingCompiler {

  override def compile(project: Project, source: Document): Opt[Unit] = {
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    for {
      _ <- Opt.?(!DumbService.getInstance(project).isDumb, "project is dumb")
      sourceFileOriginal <- Opt.fromOption(source.virtualFile, "no virtual file")
      _ <- Opt.?(acceptableExtensions contains sourceFileOriginal.getExtension, "wrong extension")
      module <- Opt.fromOption(getModule(sourceFileOriginal, projectFileIndex), "no module")
      connector = new RemoteServerConnector(
        module = module,
        sourceFileOriginal = new File(sourceFileOriginal.getCanonicalPath),
        sourceFileCopy = copyDocumentContentToFile(source),
        outputDir = None
      )
    } yield connector.compile()
  }

  private def getModule(sourceFileOriginal: VirtualFile,
                        projectFileIndex: ProjectFileIndex): Option[Module] =
    Option(projectFileIndex.getModuleForFile(sourceFileOriginal))

  private def copyDocumentContentToFile(document: Document): File = {
    val file = FileUtil.createTempFile("tmp", "", true)
    file.deleteOnExit()
    val content = document.getImmutableCharSequence
    using(IOUtils.toInputStream(content, StandardCharsets.UTF_8)) { inputStream =>
      using(new FileOutputStream(file)) { outputStream =>
        FileUtil.copy(inputStream, outputStream)
      }
    }
    file
  }

  // TODO more extensions?
  private val acceptableExtensions: Set[String] = Set(
    ScalaFileType.INSTANCE.getDefaultExtension,
  )
}
