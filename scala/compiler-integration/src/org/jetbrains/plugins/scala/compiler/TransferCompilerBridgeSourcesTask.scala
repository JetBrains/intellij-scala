package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.provider.utils.EelPathUtils
import org.jetbrains.plugins.scala.util.CompilerBridgeSourcesJars
import org.jetbrains.plugins.scala.util.compile.ScalaCompileTask

private final class TransferCompilerBridgeSourcesTask extends ScalaCompileTask {
  override protected def run(context: CompileContext): Boolean = {
    val project = context.getProject
    //noinspection ApiStatus,UnstableApiUsage
    if (!EelPathUtils.isProjectLocal(project)) {
      CompilerBridgeSourcesJars.allBridgeSources.foreach { path =>
        CompileServerLauncher.transferToRemoteProjectCacheDirectory(path, project)
      }
    }
    true
  }

  override protected def presentableName: String =
    "Prepare Scala compiler bridge sources jars"

  override protected def log: Logger = TransferCompilerBridgeSourcesTask.Log
}

private object TransferCompilerBridgeSourcesTask {
  val Log: Logger = Logger.getInstance(classOf[TransferCompilerBridgeSourcesTask])
}
