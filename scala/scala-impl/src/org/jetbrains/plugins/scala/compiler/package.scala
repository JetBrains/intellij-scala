package org.jetbrains.plugins.scala

import java.io.File

import com.intellij.openapi.projectRoots.{JavaSdkType, Sdk}
import com.intellij.openapi.util.io.FileUtil

/**
 * @author Pavel Fatin
 */
package object compiler {
  case class JDK(executable: File, tools: Option[File], name: String)

  def toJdk(sdk: Sdk): Either[String, JDK] = sdk.getSdkType match {
    case jdkType: JavaSdkType =>
      val vmExecutable = new File(jdkType.getVMExecutablePath(sdk))
      val tools = Option(jdkType.getToolsPath(sdk)).map(new File(_)) // TODO properly handle JDK 6 on Mac OS
      Right(JDK(vmExecutable, tools, sdk.getName))
    case unexpected =>
      Left(s"unexpected sdk type: '$unexpected' for sdk $sdk")
  }

  implicit class RichFile(private val file: File) extends AnyVal {
    def canonicalPath: String = FileUtil.toCanonicalPath(file.getPath)
  }
}
