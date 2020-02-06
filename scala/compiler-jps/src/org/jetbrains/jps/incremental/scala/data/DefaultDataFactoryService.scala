package org.jetbrains.jps.incremental.scala.data
import org.jetbrains.jps.model.JpsProject


object DefaultDataFactoryService extends DataFactoryService {
  override def getCompilerDataFactory: CompilerDataFactory = CompilerDataFactory

  override def getCompilationDataFactory: CompilationDataFactory = CompilationDataFactory

  override def isEnabled(project: JpsProject): Boolean = true
}
