package org.jetbrains.plugins.scala.compiler

import java.util

import com.intellij.compiler.server.BuildProcessParametersProvider
import org.jetbrains.jps.incremental.scala.data.SbtData

import scala.collection.JavaConverters._

/**
  * @author Nikolay.Tropin
  */
class ScalaBuildProcessParametersProvider extends BuildProcessParametersProvider {
  override def getVMArguments: util.List[String] = customScalaCompilerInterfaceDir().toSeq.asJava

  private def customScalaCompilerInterfaceDir(): Option[String] = {
    val key = SbtData.compilerInterfacesKey
    val custom = Option(System.getProperty(key))
    custom.map(path => s"-D$key=$path")
  }
}
