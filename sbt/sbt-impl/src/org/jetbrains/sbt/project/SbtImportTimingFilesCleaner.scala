package org.jetbrains.sbt.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.{Registry, RegistryValue, RegistryValueListener}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.startup.ProjectActivity
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.process.SbtImportTimingCollector

import java.nio.file.{Files, Path}

/**
 * Cleans up sbt import timing files when the `sbt.import.time.measurement` registry is disabled.
 *
 * @see [[SbtImportTimingCollector]]
 */
private class SbtImportTimingFilesCleaner extends ProjectActivity {

  private val Log = Logger.getInstance(getClass)

  override def execute(project: Project): Unit = {
    val registryValue = Registry.get("sbt.import.time.measurement")
    registryValue.addListener(new RegistryValueListener {
      override def afterValueChanged(value: RegistryValue): Unit = {
        if (!value.asBoolean()) {
          cleanUpSbtImportTimingFiles(project)
        }
      }
    }, project)
  }

  private def cleanUpSbtImportTimingFiles(project: Project): Unit =
    try {
      val projectDir = Path.of(SbtUtil.getWorkingDirPath(project))
      Seq(SbtImportTimingCollector.historyFile, SbtImportTimingCollector.summaryFile).foreach { fileName =>
        val file = projectDir / fileName
        Files.deleteIfExists(file)
      }
    } catch {
      case exc: Exception =>
        Log.warn(s"[SbtImportTimingFilesCleaner] Failed to clean up sbt import timing files: $exc")
    }
}