package org.jetbrains.plugins.scala
package decompiler

import com.intellij.openapi.vfs.VirtualFile

/**
 * @author ilyas
 */

trait CompiledFileAdjuster {

  protected var sourceFileName : String = ""
  protected var compiled = false
  protected var virtualFile: VirtualFile = null

  private[decompiler] def setSourceFileName(s: String) {
    sourceFileName = s
  }

  private[decompiler] def setCompiled(c: Boolean) {
    compiled = c
  }

  private[decompiler] def setVirtualFile(vf: VirtualFile) {
    virtualFile = vf
  }

}