package org.jetbrains.jps.incremental.scala.hydra

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.incremental.scala.data.CompilerData
import org.jetbrains.jps.incremental.scala.data.CompilerJars
import org.jetbrains.jps.incremental.scala.data.SbtData
import org.jetbrains.jps.incremental.scala.local.CompilerFactory
import org.jetbrains.jps.incremental.scala.local.CompilerFactoryService

class HydraCompilerFactoryService extends CompilerFactoryService {
  private val Log: JpsLogger = JpsLogger.getInstance(classOf[HydraCompilerFactoryService].getName)

  override def isEnabled(compilerData: CompilerData): Boolean = {
    Log.info(s"Called ${this.getClass.getName} extension to check if it's enabled.")
    isHydraCompiler(compilerData.compilerJars)
  }

  private def isHydraCompiler(compilerJars: Option[CompilerJars]): Boolean = compilerJars match {
    case None =>
      Log.info("Compiler jars is empty.")
      false
    case Some(jars) =>
      Log.debug("Looking for hydra in compiler jars: " + jars)
      jars.compiler.getName.contains("hydra")
  }

  override def get(sbtData: SbtData): CompilerFactory = new HydraCompilerFactory(sbtData)
}
