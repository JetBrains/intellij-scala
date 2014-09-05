package org.jetbrains.plugins.scala
package debugger

import com.intellij.debugger.engine.JavaDebugAware
import com.intellij.psi.PsiFile

/**
 * @author Alefas
 * @since 05/09/14.
 */
class ScalaJavaDebugAware extends JavaDebugAware {
  override def isBreakpointAware(psiFile: PsiFile): Boolean = {
    psiFile.getFileType == ScalaFileType.SCALA_FILE_TYPE
  }
}
