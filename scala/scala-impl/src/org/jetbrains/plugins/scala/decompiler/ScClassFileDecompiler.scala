package org.jetbrains.plugins.scala
package decompiler

import java.io.IOException

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.{PsiFile, PsiManager, SingleRootFileViewProvider}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

import scala.annotation.tailrec

final class ScClassFileDecompiler extends ClassFileDecompilers.Full {

  import ScClassFileDecompiler._

  override def accepts(file: VirtualFile): Boolean =
    isScalaFile(file) || canBeProcessed(file)

  override def getStubBuilder: ScClsStubBuilder.type = ScClsStubBuilder

  override def createFileViewProvider(file: VirtualFile, manager: PsiManager, physical: Boolean): ScSingleRootFileViewProvider = {
    val maybeContents = try {
      val decompilationResult = decompile(file)()
      if (decompilationResult.isScala) Some(decompilationResult.sourceText)
      else None
    } catch {
      case _: IOException => None
    }

    ScSingleRootFileViewProvider(physical, maybeContents)(file, manager)
  }
}

object ScClassFileDecompiler {

  sealed abstract class ScSingleRootFileViewProvider(physical: Boolean)
                                                    (implicit file: VirtualFile, manager: PsiManager)
    extends SingleRootFileViewProvider(manager, file, physical, ScalaLanguage.INSTANCE)

  private object ScSingleRootFileViewProvider {

    def apply(physical: Boolean, maybeContents: Option[String])
             (implicit file: VirtualFile, manager: PsiManager): ScSingleRootFileViewProvider =
      maybeContents.fold(new NonScalaClassFileViewProvider(physical): ScSingleRootFileViewProvider) {
        new ScalaClassFileViewProvider(physical, _)
      }

    final class ScalaClassFileViewProvider(physical: Boolean, private val contents: String)
                                          (implicit file: VirtualFile, manager: PsiManager)
      extends ScSingleRootFileViewProvider(physical) {

      override def createFile(project: Project, virtualFile: VirtualFile, fileType: FileType): PsiFile = {
        val file = new ScalaFileImpl(this)
        file.isCompiled = true
        file.virtualFile = virtualFile
        file
      }

      override def getContents: CharSequence = contents match {
        case null => decompile(getVirtualFile)().sourceText
        case _ => contents
      }

      override def createCopy(copy: VirtualFile): SingleRootFileViewProvider =
        new ScalaClassFileViewProvider(false, null)(copy, getManager)
    }

    final class NonScalaClassFileViewProvider(physical: Boolean)
                                             (implicit file: VirtualFile, manager: PsiManager)
      extends ScSingleRootFileViewProvider(physical) {

      override def createFile(project: Project, virtualFile: VirtualFile, fileType: FileType): PsiFile = null

      override def getContents: CharSequence = ""

      override def createCopy(copy: VirtualFile): SingleRootFileViewProvider =
        new NonScalaClassFileViewProvider(false)(copy, getManager)
    }

  }

  private def canBeProcessed(file: VirtualFile): Boolean = {
    val maybeParent = Option(file.getParent)

    @tailrec
    def go(prefix: String, suffix: String): Boolean = {
      if (!prefix.endsWith("$")) {
        val maybeChild = maybeParent.map(_.findChild(prefix + ".class"))
        if (maybeChild.exists(isScalaFile)) return true
      }

      split(suffix) match {
        case Some((suffixPrefix, suffixSuffix)) => go(prefix + "$" + suffixPrefix, suffixSuffix)
        case _ => false
      }
    }

    split(file.getNameWithoutExtension) match {
      case Some((prefix, suffix)) => go(prefix, suffix)
      case _ => false
    }
  }

  private[this] def split(string: String) = string.indexOf('$') match {
    case -1 => None
    case index => Some(string.substring(0, index), string.substring(index + 1))
  }
}
