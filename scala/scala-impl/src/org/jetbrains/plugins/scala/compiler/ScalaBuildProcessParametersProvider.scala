package org.jetbrains.plugins.scala.compiler

import java.util

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.data.SbtData
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode

import scala.jdk.CollectionConverters._

/**
  * @author Nikolay.Tropin
  */
class ScalaBuildProcessParametersProvider(project: Project)
  extends BuildProcessParametersProvider {
  
  override def getVMArguments: util.List[String] =
    customScalaCompilerInterfaceDir().toSeq.asJava

  private def customScalaCompilerInterfaceDir(): Option[String] = {
    val key = SbtData.compilerInterfacesKey
    val custom = Option(System.getProperty(key))
    custom.map(path => s"-D$key=$path")
  }

  override def isProcessPreloadingEnabled: Boolean =
    !ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)
}
