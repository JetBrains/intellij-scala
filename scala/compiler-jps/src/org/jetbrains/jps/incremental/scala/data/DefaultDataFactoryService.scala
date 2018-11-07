package org.jetbrains.jps.incremental.scala.data
import org.jetbrains.jps.model.JpsProject


object DefaultDataFactoryService extends DataFactoryService {
  override def getCompilerDataFactory: CompilerDataFactory = CompilerData

  override def getCompilationDataFactory: CompilationDataFactory = CompilationData

  override def isEnabled(project: JpsProject): Boolean = true
}
