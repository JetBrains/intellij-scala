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

    def isScalaCompiledClassFile: Boolean = topLevelScalaClass.nonEmpty

    def isScalaInnerClass: Boolean = {
      val fileName = virtualFile.getNameWithoutExtension
      !topLevelScalaClass.contains(fileName)
    }
  }

  private def canBeDecompiled(file: VirtualFile): Boolean = DecompilationResult.tryDecompile(file).isDefined

  private class PrefixIterator(fullName: String) extends Iterator[String] {
    private var currentPrefixEnd = -1

    def hasNext: Boolean = currentPrefixEnd < fullName.length

    def next(): String = {
      val prefix = nextPrefix(fullName, currentPrefixEnd)
      currentPrefixEnd = prefix.length
      prefix
    }

    private def nextPrefix(fullName: String, previousPrefixEnd: Int = -1): String = {
      if (previousPrefixEnd == fullName.length) null
      else {
        val nextDollarIndex = fullName.indexOf('$', previousPrefixEnd + 1)

        if (nextDollarIndex >= 0) fullName.substring(0, nextDollarIndex)
        else fullName
      }
    }
  }
}
