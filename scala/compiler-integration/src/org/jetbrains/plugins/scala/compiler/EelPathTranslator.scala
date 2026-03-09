package org.jetbrains.plugins.scala.compiler

import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, LocalEelDescriptor}
import org.jetbrains.jps.incremental.scala.remote.PathTranslator
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

object EelPathTranslator extends PathTranslator {
  //noinspection ApiStatus,UnstableApiUsage
  /**
   * Translates the path to be local within the remote environment.
   * If the path is already local, it is returned as is.
   */
  override def translate(path: Path): String = {
    val canonical = path.toCanonicalPath
    val eelPath = EelNioBridgeServiceKt.asEelPath(canonical)
    eelPath.getDescriptor match {
      case LocalEelDescriptor.INSTANCE => path.toString
      case _ => eelPath.toString
    }
  }
}
