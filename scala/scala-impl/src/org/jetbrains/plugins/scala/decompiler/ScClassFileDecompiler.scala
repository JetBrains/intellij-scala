package org.jetbrains.plugins.scala
package decompiler

import java.io.IOException

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers

import scala.annotation.tailrec

final class ScClassFileDecompiler extends ClassFileDecompilers.Full {

  import DecompilerUtil.isScalaFile
  import ScClassFileDecompiler._

  override def accepts(file: VirtualFile): Boolean = try {
    isScalaFile(file, file.contentsToByteArray) || canBeProcessed(file)
  } catch {
    case _: UnsupportedOperationException | // why do we need to handle this?
         _: IOException => false
  }

  override def getStubBuilder: ScClsStubBuilder.type = ScClsStubBuilder

  override def createFileViewProvider(file: VirtualFile, manager: PsiManager, physical: Boolean) =
    new ScClassFileViewProvider(manager, file, physical, isScalaFile(file))

}

object ScClassFileDecompiler {

  private def canBeProcessed(file: VirtualFile): Boolean = {
    val maybeParent = Option(file.getParent)

    @tailrec
    def go(prefix: String, suffix: String): Boolean = {
      if (!prefix.endsWith("$")) {
        val maybeChild = maybeParent.map(_.findChild(prefix + ".class"))
        if (maybeChild.exists(DecompilerUtil.isScalaFile)) return true
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
