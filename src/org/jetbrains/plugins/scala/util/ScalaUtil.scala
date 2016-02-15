package org.jetbrains.plugins.scala.util

import com.intellij.openapi.application.{PathManager, ApplicationManager}
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
    PathUtil.getJarPathForClass(classOf[Client]).replace("compiler-settings", "scala-plugin-runners")
  }

  def testingSupportTestPath(): String = {
    PathUtil.getJarPathForClass(classOf[Client]).replace("compiler-settings", "Runners")
  }

  def getScalaPluginSystemPath: String = {
    PathManager.getSystemPath + "/scala"
  }
}