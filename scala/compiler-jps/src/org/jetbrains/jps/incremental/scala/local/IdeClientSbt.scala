package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.{BuildRootDescriptor, BuildTarget}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.{CompileContext, FSOperations}

import java.nio.file.{Path, Paths}

class IdeClientSbt(compilerName: String,
                   context: CompileContext,
                   chunk: ModuleChunk,
                   consumer: OutputConsumer,
                   sourceToTarget: Path => Option[BuildTarget[_ <: BuildRootDescriptor]])
        extends IdeClient(compilerName, context, chunk) {

  override def generated(source: Path, outputFile: Path, name: String): Unit = {
    val target = sourceToTarget(source).getOrElse {
      throw new RuntimeException("Unknown source file: " + source)
    }
    val compiledClass = new LazyCompiledClass(outputFile, source, name)
    consumer.registerCompiledClass(target, compiledClass)
  }

  override def sourceStarted(source: String): Unit = {
    FSOperations.markDirty(context, CompilationRound.NEXT, Paths.get(source).toFile)
  }
}