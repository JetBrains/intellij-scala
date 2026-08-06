package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher.CompileServerProblem

import java.nio.file.Path

final case class JDK(executable: Path, tools: Option[Path], name: String, version: Option[JavaSdkVersion])

object JDK {
  def fromSdk(sdk: Sdk): Either[CompileServerProblem, JDK] = sdk.getSdkType match {
    case jdkType: JavaSdk =>
      val vmExecutable = Path.of(jdkType.getVMExecutablePath(sdk))
      val tools = Option(jdkType.getToolsPath(sdk)).map(Path.of(_)) // TODO properly handle JDK 6 on Mac OS
      val version = Option(jdkType.getVersion(sdk))
      Right(JDK(vmExecutable, tools, sdk.getName, version))
    case unexpected =>
      Left(CompileServerProblem.Error(CompilerIntegrationBundle.message("unexpected.sdk.type.for.sdk", unexpected, sdk)))
  }
}
