package org.jetbrains.plugins.scala.decompileToJava

import java.io.File
import java.util.jar

import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.java.decompiler.IdeaLogger
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler
import org.jetbrains.java.decompiler.main.extern.{IBytecodeProvider, IFernflowerPreferences, IResultSaver}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.JavaConverters._
import scala.collection.mutable

private class ScalaDecompilerServiceImpl extends ScalaDecompilerService {
  import ScalaDecompilerServiceImpl._

  override def decompile(file: ScalaFile): Either[DecompilationError, String] = if (file.isCompiled) {
    try {
      val mappings = inReadAction { mappingsForClassfile(file.getVirtualFile) }
      val saver    = new ScalaResultSaver

      val options = Map(
        IFernflowerPreferences.REMOVE_BRIDGE -> 0.asInstanceOf[AnyRef]
      ).asJava

      val provider: IBytecodeProvider = (externalPath, _) => {
        val path = new File(FileUtil.toSystemIndependentName(externalPath))
        mappings.get(path).map(_()).orNull
      }

      val decompiler = new BaseDecompiler(provider, saver, options, new IdeaLogger())
      mappings.foreach { case (path, _) => decompiler.addSource(path) }
      decompiler.decompileContext()
      Right(saver.result)
    } catch {
      case e: IdeaLogger.InternalException =>
        val error = e.toOption
        val message = error.map(_.getMessage).getOrElse("Unknown decompilation failure.")
        Left(DecompilationError(message, error))
    }
  } else Left(DecompilationError(s"Unable to decompile ${file.name}"))

  private[this] def isClassGeneratedFrom(sourceName: String, classfile: VirtualFile): Boolean =
    classfile.getFileType == StdFileTypes.CLASS && {
      val name = classfile.getNameWithoutExtension
      name == sourceName || name.startsWith(s"$sourceName$$")
    }

  private[this] def mappingsForClassfile(file: VirtualFile): Map[File, FileContents] =
    file.getParent.getChildren.iterator.collect {
      case child if isClassGeneratedFrom(file.getNameWithoutExtension, child) =>
        new File(child.getPath) -> getFileContents(child)
    }.toMap
}

object ScalaDecompilerServiceImpl {
  type FileContents = () => Array[Byte]

  private def getFileContents(vf: VirtualFile): FileContents =
    () => vf.contentsToByteArray(false)

  private class ScalaResultSaver extends IResultSaver {
    private[this] val decompiledTexts = mutable.Map.empty[String, String]

    def result: String = decompiledTexts.map { case (filename, text) =>
      s"""|//decompiled from ${filename.stripSuffix(".java")}.class
          |$text
        """.stripMargin
    }.mkString

    override def saveClassFile(path: String, qname: String, entry: String, content: String, mapping: Array[Int]): Unit =
      if (entry != null && content != null) decompiledTexts += entry -> content

    override def saveClassEntry(path: String, archive: String, qname: String, entry: String, content: String): Unit = ()
    override def saveDirEntry(path: String, archive: String, entry: String): Unit = ()
    override def closeArchive(path: String, name: String): Unit = ()
    override def saveFolder(path: String): Unit = ()
    override def copyFile(source: String, path: String, entry: String): Unit = ()
    override def createArchive(path: String, name: String, manifest: jar.Manifest): Unit = ()
    override def copyEntry(source: String, path: String, name: String, entry: String): Unit = ()
  }
}
