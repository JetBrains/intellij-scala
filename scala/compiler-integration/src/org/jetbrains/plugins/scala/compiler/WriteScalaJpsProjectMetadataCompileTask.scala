package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.jps.incremental.scala.ScalaJpsProjectMetadataConstants
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.compile.ScalaCompileTask

import java.io.{BufferedOutputStream, DataInputStream, DataOutputStream, IOException}
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.Using

private final class WriteScalaJpsProjectMetadataCompileTask extends ScalaCompileTask {
  import WriteScalaJpsProjectMetadataCompileTask.Log

  override protected def run(context: CompileContext): Boolean = {
    writeJpsProjectMetadata(force = context.isRebuild, context.getProject)
    true
  }

  override protected def presentableName: String = "Writing Scala JPS project metadata to disk"

  override protected def log: Logger = Log

  /**
   * Writes the Scala project metadata to disk which the JPS process expects while running the build.
   *
   * @param force write the latest project metadata regardless of the previous state on disk
   * @param project the project instance
   */
  private def writeJpsProjectMetadata(force: Boolean, project: Project): Unit = {
    val buildManager = BuildManager.getInstance()
    val projectSystemDirectory = buildManager.getProjectSystemDir(project)

    val configFilePath = projectSystemDirectory / ScalaJpsProjectMetadataConstants.ScalaJpsProjectMetadataFileName

    val projectRootManager = ProjectRootManager.getInstance(project)
    val crc = projectRootManager.getModificationCount

    val crcFilePath = configFilePath.resolveSibling(ScalaJpsProjectMetadataConstants.ScalaJpsProjectMetadataCrcFileName)

    if (!force && crcFilePath.exists) {
      readLastCrcFromDisk(crcFilePath) match {
        case Some(lastCrc) =>
          if (lastCrc == crc)
            return // Project has not changed, there's no need to write the same project metadata to disk.
          Log.debug(s"Project metadata changed: lastCrc = $lastCrc, currentCrc = $crc")
        case None =>
          Log.debug("Could not read Scala JPS project metadata from disk, the file was most likely not produced yet")
      }
    }

    val projectMetadata = ProjectMetadataUtil.jpsProjectMetadata(project)

    val writeToDiskTask: Runnable = () => {
      if (!project.isDefault) {
        buildManager.clearState(project)
      }
      try {
        JDOMUtil.write(projectMetadata.asXml, configFilePath)
        Using.resource(new DataOutputStream(
          new BufferedOutputStream(
            Files.newOutputStream(crcFilePath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
          )
        ))(_.writeLong(crc))
      } catch {
        case e: IOException =>
          Log.error("Unable to write Scala JPS project metadata file", e)
          throw new RuntimeException(e)
      }
    }

    buildManager.runCommand(writeToDiskTask)
  }

  private def readLastCrcFromDisk(crcFilePath: Path): Option[Long] =
    Using(new DataInputStream(Files.newInputStream(crcFilePath, StandardOpenOption.READ)))(_.readLong()).toOption
}

private object WriteScalaJpsProjectMetadataCompileTask {
  private val Log: Logger = Logger.getInstance(classOf[WriteScalaJpsProjectMetadataCompileTask])
}
