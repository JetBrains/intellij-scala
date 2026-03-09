package org.jetbrains.plugins.scala.compiler

import com.intellij.platform.eel.EelDescriptor

import java.nio.file.Path
import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, LocalEelDescriptor}

object EelCompilerUtils {
  /**
   * Given a [[java.nio.file.Path]] instance, returns a local path string which can be used inside the target machine.
   * For example, it returns a UNIX filesystem path for a given path inside WSL.
   *
   * @note The code is duplicated in `org.jetbrains.sbt.eelPathExtensions`. Should be deduplicated in the future after
   *       we fully migrate to Scala 3.
   */
  def asTargetLocalPathString(path: Path, eelDescriptor: EelDescriptor): String =
    eelDescriptor match {
      case LocalEelDescriptor.INSTANCE =>
        path.toString
      case remote =>
        val eelPath = EelNioBridgeServiceKt.asEelPath(path, remote)
        eelPath.toString
    }
}
