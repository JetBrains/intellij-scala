package org.jetbrains.plugins.scala.decompileToJava

import com.intellij.openapi.components.ServiceManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

trait ScalaDecompilerService {
  def decompile(sourceFile: ScalaFile): Either[DecompilationError, String]
}

object ScalaDecompilerService {
  def apply(): ScalaDecompilerService = ServiceManager.getService(classOf[ScalaDecompilerService])
}
