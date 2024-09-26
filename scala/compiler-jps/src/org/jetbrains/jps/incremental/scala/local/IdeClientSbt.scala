package org.jetbrains.jps.incremental.scala
package local

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.{BuildRootDescriptor, BuildTarget}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.{CompileContext, FSOperations}

import java.io.File
import java.util
import scala.jdk.CollectionConverters._
import scala.util.control.Exception._

class IdeClientSbt(compilerName: String,
                   context: CompileContext,
                   chunk: ModuleChunk,
                   consumer: OutputConsumer,
                   sourceToTarget: File => Option[BuildTarget[_ <: BuildRootDescriptor]])
        extends IdeClient(compilerName, context, chunk) {

  override def generated(source: File, outputFile: File, name: String): Unit = {
    invalidateBoundForms(source)
    val target = sourceToTarget(source).getOrElse {
      throw new RuntimeException("Unknown source file: " + source)
    }
    val compiledClass = new LazyCompiledClass(outputFile, source, name)
    consumer.registerCompiledClass(target, compiledClass)
  }

  override def sourceStarted(source: String): Unit = {
    FSOperations.markDirty(context, CompilationRound.NEXT, new File(source))
  }

  // TODO Expect JPS compiler in UI-designer to take generated class events into account
  private val FormsToCompileKey = catching(classOf[ClassNotFoundException], classOf[NoSuchFieldException]).opt {
    val field = Class.forName("org.jetbrains.jps.uiDesigner.compiler.FormsBuilder").getDeclaredField("FORMS_TO_COMPILE")
    field.setAccessible(true)
    field.get(null).asInstanceOf[Key[util.Map[File, util.Collection[File]]]]
  }

  private def invalidateBoundForms(source: File): Unit = {
    FormsToCompileKey.foreach { key =>
      val boundForms: Option[Iterable[File]] = {
        val target = sourceToTarget(source).getOrElse(chunk.representativeTarget())
        //noinspection ApiStatus
        val sourceToForm = context.getProjectDescriptor.dataManager.getSourceToFormMap(target)
        val sourcePath = FileUtil.toCanonicalPath(source.getPath)
        Option(sourceToForm.getOutputs(sourcePath)).map(_.asScala.map(new File(_)))
      }

      boundForms.foreach { forms =>
        val formsToCompile = Option(key.get(context)).getOrElse(new util.HashMap[File, util.Collection[File]]())
        formsToCompile.put(source, forms.toVector.asJava)
        key.set(context, formsToCompile)
      }
    }
  }
}