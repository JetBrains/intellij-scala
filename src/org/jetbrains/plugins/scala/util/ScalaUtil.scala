package org.jetbrains.plugins.scala.util

import java.io.File

import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.util.PathUtil
import org.jetbrains.jps.incremental.scala.Client

/**
 * User: Alexander Podkhalyuzin
 * Date: 16.11.11
 */

object ScalaUtil {
  def writeAction[T](project: Project)(callback: => T): T = {
    ApplicationManager.getApplication.runWriteAction(new Computable[T] {
      def compute(): T = callback
    })
  }
  
  def readAction[T](project: Project)(callback: => T): T = {
    ApplicationManager.getApplication.runReadAction(new Computable[T] {
      def compute(): T = callback
    })
  }

  def runnersPath(): String = {
    PathUtil.getJarPathForClass(classOf[Client]).replace("jpsShared", "scala-plugin-runners")
  }

  def testingSupportTestPath(): String = {
    PathUtil.getJarPathForClass(classOf[Client]).replace("jpsShared", "Runners")
  }

  def getScalaPluginSystemPath: String = {
    PathManager.getSystemPath + "/scala"
  }

  def createTmpDir(prefix: String): File = {
    val tmpDir = File.createTempFile(prefix, "")
    tmpDir.delete()
    tmpDir.mkdir()
    tmpDir
  }
}