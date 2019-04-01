package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.vfs.VirtualFile

package object compiled {

  private val ClassFileExtension = JavaClassFileType.INSTANCE.getDefaultExtension

  private def classFileName(withoutExtension: String) = s"$withoutExtension.$ClassFileExtension"

  implicit class VirtualFileExt(val virtualFile: VirtualFile) extends AnyVal {

    private def topLevelScalaClass: Option[String] = {
      val parent = virtualFile.getParent

      if (parent == null || virtualFile.getExtension != ClassFileExtension)
        return None

      val prefixIterator = new PrefixIterator(virtualFile.getNameWithoutExtension)

      prefixIterator.filterNot(_.endsWith("$"))
        .find { prefix =>
          val candidate = parent.findChild(classFileName(prefix))
          candidate != null && canBeDecompiled(candidate)
        }
    }

    def isScalaCompiledClassFile: Boolean = topLevelScalaClass.isDefined

    def isScalaTopLevelClass: Boolean = topLevelScalaClass.contains(virtualFile.getNameWithoutExtension)
  }

  private def canBeDecompiled(file: VirtualFile): Boolean = DecompilationResult.tryDecompile(file).isDefined

  private class PrefixIterator(private val string: String) extends Iterator[String] {

    private val stringLength = string.length
    private var endIndex = -1

    def hasNext: Boolean = endIndex < stringLength

    def next(): String = endIndex match {
      case `stringLength` => throw new NoSuchElementException("Prefix length equals string length")
      case current =>
        endIndex = string.indexOf('$', current + 1) match {
          case -1 => stringLength
          case newEndIndex => newEndIndex
        }
        string.substring(0, endIndex)
    }
  }

}
