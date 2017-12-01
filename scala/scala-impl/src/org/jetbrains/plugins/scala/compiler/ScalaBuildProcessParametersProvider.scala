package org.jetbrains.plugins.scala.compiler

import java.util

import com.intellij.compiler.server.BuildProcessParametersProvider
import org.jetbrains.jps.incremental.scala.data.SbtData
import scala.collection.JavaConverters._

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.ProjectExt

/**
  * @author Nikolay.Tropin
  */
class ScalaBuildProcessParametersProvider(project: Project) extends BuildProcessParametersProvider {

  override def getVMArguments: util.List[String] = {
    if (project.hasScala)
      customScalaCompilerInterfaceDir().toSeq.asJava
    else
      super.getVMArguments
  }

  override def getClassPath: util.List[String] = {
    if (project.hasScala)
      CompileServerLauncher.jpsProcessClasspath.map(_.getCanonicalPath).asJava
    else
      super.getClassPath
  }

  private def customScalaCompilerInterfaceDir(): Option[String] = {
    val key = SbtData.compilerInterfacesKey
    val custom = Option(System.getProperty(key))
    custom.map(path => s"-D$key=$path")
  }
}
