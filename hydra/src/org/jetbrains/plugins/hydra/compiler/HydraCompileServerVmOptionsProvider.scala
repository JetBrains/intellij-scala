package org.jetbrains.plugins.hydra.compiler

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.CompileServerVmOptionsProvider

class HydraCompileServerVmOptionsProvider extends CompileServerVmOptionsProvider {
  override def vmOptionsFor(project: Project): Seq[String] =
    HydraCompilerSettingsManager.getHydraLogJvmParameter(project).toSeq
}
