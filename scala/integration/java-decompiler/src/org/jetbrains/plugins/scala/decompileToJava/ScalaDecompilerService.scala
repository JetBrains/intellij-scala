package org.jetbrains.plugins.scala
package decompileToJava

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.api.ScFile

import scala.util.Try

trait ScalaDecompilerService {
  def decompile(sourceFile: ScFile): Try[String]
}

object ScalaDecompilerService {
  def apply(): ScalaDecompilerService = ApplicationManager.getApplication.getService(classOf[ScalaDecompilerService])
}
