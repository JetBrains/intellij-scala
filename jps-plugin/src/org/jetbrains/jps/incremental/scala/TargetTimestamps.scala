package org.jetbrains.jps.incremental.scala

import java.io._
import org.jetbrains.jps.incremental.{CompileContext, ModuleBuildTarget}

/**
 * @author Pavel Fatin
 */
class TargetTimestamps(context: CompileContext) {
  private val paths = context.getProjectDescriptor.dataManager.getDataPaths

  def get(target: ModuleBuildTarget): Option[Long] = {
    Some(timestampFile(target)).filter(_.exists).flatMap { file =>
      val in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))

      try {
        Some(in.readLong())
      } catch {
        case _: IOException => None
      } finally {
        in.close()
      }
    }
  }

  def set(target: ModuleBuildTarget, timestamp: Long) {
    val out = {
      val file = timestampFile(target)
      new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))
    }

    try {
      out.writeLong(timestamp)
    } finally {
      out.close()
    }
  }

  private def timestampFile(target: ModuleBuildTarget): File = {
    paths.getTargetDataRoot(target)
    new File(paths.getTargetDataRoot(target), "timestamp.dat")
  }
}
