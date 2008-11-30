package org.jetbrains.plugins.scala.decompiler
/**
 * @author ilyas
 */

trait CompiledFileAdjuster {

  protected var sourceFileName : String = ""
  protected var compiled = false

  private[decompiler] def setSourceFileName(s: String) {
    sourceFileName = s
  }

  private[decompiler] def setCompiled(c: Boolean) {
    compiled = c
  }

}