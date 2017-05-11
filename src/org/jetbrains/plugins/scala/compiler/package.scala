package org.jetbrains.plugins.scala

import java.io.File

import com.intellij.openapi.projectRoots.{JavaSdkType, Sdk}
import com.intellij.openapi.util.io.FileUtil

/**
 * @author Pavel Fatin
 */
package object compiler {
  case class JDK(executable: File, tools: File)

  def toJdk(sdk: Sdk): Option[JDK] = sdk.getSdkType match {
    case jdkType: JavaSdkType =>
      val vmExecutable = new File(jdkType.getVMExecutablePath(sdk))
      val tools = new File(jdkType.getToolsPath(sdk)) // TODO properly handle JDK 6 on Mac OS
      Some(JDK(vmExecutable, tools))
    case _ => None
  }

  implicit class RichFile(val file: File) extends AnyVal {
    def canonicalPath: String = FileUtil.toCanonicalPath(file.getPath)
  }
}
