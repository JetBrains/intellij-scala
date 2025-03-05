package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
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
   * Writes the Scala project metadata to disk which the JPS process expects while runinng the build.
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

    val crcFilePath = configFilePath.resolveSibling("scala-jps-project-metadata.crc")

    if (!force && crcFilePath.exists) {
      try {
        val lastCrc = readLastCrcFromDisk(crcFilePath)
        if (lastCrc == crc) return // Project has not changed.
        Log.debug(s"Project metadata changed: lastCrc = $lastCrc, currentCrc = $crc")
      } catch {
        case e: IOException =>
          Log.error("Unable to read or find Scala JPS project metadata crc file", e)
      }
    }

    val element = new Element("scala-jps-project-metadata")
    val modulesWithScalaSdkElement = new Element(ScalaJpsProjectMetadataConstants.ModulesWithScalaSdkElement)
    project.modulesWithScala.foreach { module =>
      val name = module.getName
      val moduleElement = new Element(ScalaJpsProjectMetadataConstants.ModuleElement)
      moduleElement.setAttribute(ScalaJpsProjectMetadataConstants.NameAttribute, name)
      modulesWithScalaSdkElement.addContent(moduleElement)
    }
    element.addContent(modulesWithScalaSdkElement)

    val writeToDiskTask: Runnable = () => {
      if (!project.isDefault) {
        buildManager.clearState(project)
      }
      try {
        JDOMUtil.write(element, configFilePath)
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

  private def readLastCrcFromDisk(crcFilePath: Path): Long =
    Using.resource(new DataInputStream(Files.newInputStream(crcFilePath, StandardOpenOption.READ)))(_.readLong())
}

private object WriteScalaJpsProjectMetadataCompileTask {
  private val Log: Logger = Logger.getInstance(classOf[WriteScalaJpsProjectMetadataCompileTask])
}
