package org.jetbrains.jps.incremental.scala.hydra.data

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}

import org.jetbrains.jps.incremental.scala.data.{CompilerDataFactory => ScalaCompilerDataFactory}
import org.jetbrains.jps.incremental.scala.data.{CompilationDataFactory => ScalaCompilationDataFactory}
import org.jetbrains.jps.incremental.scala.data.DataFactoryService
import org.jetbrains.jps.incremental.scala.hydra.HydraExtensionService

class HydraDataFactoryService extends DataFactoryService with HydraExtensionService {
  override protected lazy val Log = JpsLogger.getInstance(classOf[HydraExtensionService].getName)

  override def getCompilerDataFactory: ScalaCompilerDataFactory = CompilerDataFactory

  override def getCompilationDataFactory: ScalaCompilationDataFactory = CompilationDataFactory
}
