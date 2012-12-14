package org.jetbrains.jps.incremental.scala

import java.io._
import org.jetbrains.jps.incremental.{CompileContext, ModuleBuildTarget}
import com.intellij.openapi.util.io.FileUtil

/**
 * @author Pavel Fatin
 */
class TargetClasspaths(context: CompileContext) {
  private val paths = context.getProjectDescriptor.dataManager.getDataPaths

  def get(target: ModuleBuildTarget): Option[Set[String]] = {
    val bytes = Some(timestampFile(target)).filter(_.exists).flatMap { file =>
      try {
        Some(FileUtil.loadFileBytes(file))
      } catch {
        case _: IOException => None
      }
    }

    bytes.flatMap { data =>
      val lines = new String(data, TargetClasspaths.Encoding).split("\n")

      if (lines.length > 0) Some(lines.toSet) else None
    }
  }

  def set(target: ModuleBuildTarget, classpath: Set[String]) {
    val file = timestampFile(target)
    val bytes = classpath.mkString("\n").getBytes(TargetClasspaths.Encoding)

    FileUtil.writeToFile(file, bytes)
  }

  private def timestampFile(target: ModuleBuildTarget): File = {
    paths.getTargetDataRoot(target)
    new File(paths.getTargetDataRoot(target), "classpath.dat")
  }
}

private object TargetClasspaths {
  private val Encoding = "UTF-8"
}
