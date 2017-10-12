package org.jetbrains.jps.incremental.scala.sbtzinc

import java.nio.file.StandardOpenOption._
import java.nio.file._

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.SettingsManager
import org.jetbrains.jps.incremental.scala.data.CompilationData

import scala.collection.JavaConverters._

/**
  * Cache and check scala, java, etc., options for changes across build runs
  */
object CompilerOptionsStore {
  private val cacheDir = "strato-options-cache"
  private val moduleCacheFileSuffix = "_options-cache.txt"

  /**
    * @return true if compiler options change was detected
    */
  def updateCompilerOptionsCache(context: CompileContext, chunk: ModuleChunk, moduleNames: Seq[String]): Boolean = {
    val scalacOptsCacheFile = getCacheFileFor(context, moduleNames)
    val previousScalacOpts = readCachedOptions(scalacOptsCacheFile)
    val currentOpts = getCurrentOptions(context, chunk)
    val changeDetected = previousScalacOpts.isEmpty || previousScalacOpts.get != currentOpts

    if (changeDetected) writeToCache(scalacOptsCacheFile, currentOpts)

    changeDetected
  }

  private def getCacheFileFor(context: CompileContext, moduleNames: Seq[String]): Path = {
    val dataPath = context.getProjectDescriptor.dataManager.getDataPaths.getTargetsDataRoot.toPath
    val scalacOptsDataFile = dataPath.resolve(cacheDir).resolve(moduleNames.head + moduleCacheFileSuffix)
    scalacOptsDataFile
  }

  private def getCurrentOptions(context: CompileContext, chunk: ModuleChunk): String = {
    val target = chunk.representativeTarget
    val module = target.getModule
    val compilerSettings = SettingsManager.getProjectSettings(module.getProject).getCompilerSettings(chunk)
    // NOTE
    // The below items don't guarantee 100% correctness (we might skip Zinc compilation when in fact it was necessary)
    // It's heuristic aimed at being accurate for the common usecases and not incuring too big of an overhead
    // especially for small compilations
    val javaOpts = "javaOpts: " + CompilationData.javaOptionsFor(context, chunk).mkString(" ")
    val scalaOpts = "scalaOpts: " + CompilationData.scalaOptionsFor(compilerSettings, chunk).mkString(" ") +
      compilerSettings.getSbtIncrementalOptions.productIterator.mkString(" ") +
      compilerSettings.getCompileOrder
    val stringifiedOpts = Array(scalaOpts, javaOpts).mkString("\n")
    stringifiedOpts
  }

  private def writeToCache(scalacOptsDataFile: Path, currentScalacOpts: String): Unit = {
    if (!Files.exists(scalacOptsDataFile.getParent)) {
      Files.createDirectories(scalacOptsDataFile.getParent)
    }
    Files.write(scalacOptsDataFile, Seq(currentScalacOpts).asJava, WRITE, TRUNCATE_EXISTING, CREATE)
  }

  private def readCachedOptions(scalacOptsDataFile: Path): Option[String] = {
    if (Files.exists(scalacOptsDataFile)) {
      Some(Files.readAllLines(scalacOptsDataFile).asScala.mkString("\n"))
    } else None
  }
}
