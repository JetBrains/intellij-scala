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
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.util.Opt

trait DocumentCompiler {

  def compile(project: Project, source: Document): Opt[Unit]
}

class DocumentCompilerImpl
  extends DocumentCompiler {

  override def compile(project: Project, source: Document): Opt[Unit] = {
    val index = ProjectFileIndex.getInstance(project)
    for {
      _ <- Opt.?(!DumbService.getInstance(project).isDumb, "project is dumb")
      sourceFileOriginal <- Opt.fromOption(source.virtualFile, "no virtual file")
      _ <- Opt.?(isInSources(sourceFileOriginal, index), "file not in the sources")
      _ <- Opt.?(acceptableExtensions contains sourceFileOriginal.getExtension, "wrong extension")
      module <- Opt.fromOption(getModule(sourceFileOriginal, index), "no module")
      sourceFileCopy = copyDocumentContentToFile(source)
      connector = new DocumentRemoteServerConnector(
        module = module,
        sourceFileOriginal = new File(sourceFileOriginal.getCanonicalPath),
        sourceFileCopy = sourceFileCopy
      )
    } yield connector.compile()
  }

  private def isInSources(sourceFileOriginal: VirtualFile,
                          index: ProjectFileIndex): Boolean = {
    val rootType = index.getSourceFolder(sourceFileOriginal).getRootType
    JavaModuleSourceRootTypes.SOURCES contains rootType
  }

  private def getModule(sourceFileOriginal: VirtualFile,
                        index: ProjectFileIndex): Option[Module] =
    Option(index.getModuleForFile(sourceFileOriginal))

  private def copyDocumentContentToFile(document: Document): File = {
    val file = FileUtil.createTempFile("tmp", "", true)
    val content = document.getImmutableCharSequence
    using(IOUtils.toInputStream(content, StandardCharsets.UTF_8)) { inputStream =>
      using(new FileOutputStream(file)) { outputStream =>
        FileUtil.copy(inputStream, outputStream)
      }
    }
    file
  }

  private val acceptableExtensions: Set[String] = Set(
    ScalaFileType.INSTANCE.getDefaultExtension,
  )
}