package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.model.JpsProject

trait DataFactoryService {
  def isEnabled(project: JpsProject): Boolean
  def getCompilerDataFactory: CompilerDataFactory
  def getCompilationDataFactory: CompilationDataFactory
}
