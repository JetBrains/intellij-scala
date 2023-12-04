package org.jetbrains.plugins.scala.bsp.flow.open

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.extension.points.BspConnectionDetailsGeneratorExtension
import org.jetbrains.plugins.bsp.flow.open.wizard.{ConnectionFileOrNewConnection, ImportProjectWizardStep}
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants

import java.io.OutputStream
import java.nio.file.Path
import java.util
import java.util.concurrent.TimeUnit
import scala.collection.immutable.List
import scala.jdk.CollectionConverters._

class ScalaBspDetailsConnectionGenerator extends BspConnectionDetailsGeneratorExtension {
  override def id(): String = ScalaPluginConstants.ID

  override def displayName(): String = "Scala"

  override def canGenerateBspConnectionDetailsFile(projectPath: VirtualFile): Boolean =
    true // for now we assume that this check is redundant since it is already done earlier

  override def generateBspConnectionDetailsFile(projectPath: VirtualFile, outputStream: OutputStream, project: Project): VirtualFile = {
    val process = new ProcessBuilder("coursier", "launch", "sbt", "--", "bspConfig").start.waitFor(2, TimeUnit.MINUTES)

    getChild(projectPath, List(".bsp", "sbt.json").asJava)
  }

  override def calculateImportWizardSteps(path: Path, observableMutableProperty: ObservableMutableProperty[ConnectionFileOrNewConnection]): util.List[ImportProjectWizardStep] = ???

  override def executeAndWait(list: util.List[String], virtualFile: VirtualFile, outputStream: OutputStream, project: Project): Unit = ???

  override def getChild(root: VirtualFile, path: util.List[String]): VirtualFile = {
    val pathAsScala = path.asScala
    val found = pathAsScala.foldLeft(root) { (vf, child) =>
      vf.refresh(false, false)
      vf.findChild(child)
    }
    found.refresh(false, false)
    found
  }
}

