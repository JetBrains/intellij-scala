package org.jetbrains.jps.incremental.scala

import java.io._

import org.jetbrains.jps.incremental.{CompileContext, ModuleBuildTarget}

import scala.util.Using

class TargetTimestamps(context: CompileContext) {
  private val paths = context.getProjectDescriptor.dataManager.getDataPaths

  def get(target: ModuleBuildTarget): Option[Long] = {
    Some(timestampFile(target)).filter(_.exists).flatMap { file =>
      Using.resource(new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) { in =>
        try {
          Some(in.readLong())
        } catch {
          case _: IOException =>
            None
        }
      }
    }
  }

  def set(target: ModuleBuildTarget, timestamp: Long): Unit = {
    val file = timestampFile(target)

    Using.resource(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) { out =>
      out.writeLong(timestamp)
    }
  }

  private def timestampFile(target: ModuleBuildTarget): File = {
    paths.getTargetDataRoot(target)
    new File(paths.getTargetDataRoot(target), TargetTimestamps.Filename)
  }
}

private object TargetTimestamps {
  private val Filename = "timestamp.dat"
}