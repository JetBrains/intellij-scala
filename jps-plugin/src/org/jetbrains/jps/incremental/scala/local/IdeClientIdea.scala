package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.{Utils, CompileContext}
import org.jetbrains.jps.builders.java.dependencyView.Callbacks
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import scala.collection._
import java.io.{IOException, File}
import java.util.Collections
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.org.objectweb.asm.ClassReader

/**
 * Nikolay.Tropin
 * 11/18/13
 */
class IdeClientIdea(compilerName: String,
                      context: CompileContext,
                      modules: Seq[String],
                      consumer: OutputConsumer,
                      mappingsCallback: Callbacks.Backend,
                      successfullyCompiled: mutable.Set[File])
        extends IdeClient(compilerName, context, modules, consumer) {

  val tempSuccessfullyCompiled: mutable.Set[File] = mutable.Set[File]()

  //logic is taken from org.jetbrains.jps.incremental.java.OutputFilesSink.save
  def generated(source: File, outputFile: File, name: String): Unit = {
    val compiledClass = new LazyCompiledClass(outputFile, source, name)
    val content = compiledClass.getContent
    var isTemp: Boolean = false
    val isClassFile = outputFile.getName.endsWith(".class")

    if (source != null && content != null) {
      val sourcePath: String = FileUtil.toSystemIndependentName(source.getPath)
      val rootDescriptor = context.getProjectDescriptor.getBuildRootIndex.findJavaRootDescriptor(context, source)
      if (rootDescriptor != null) {
        isTemp = rootDescriptor.isTemp
        if (!isTemp) {
          try {
            if (isClassFile) consumer.registerCompiledClass(rootDescriptor.target, compiledClass)
            else consumer.registerOutputFile(rootDescriptor.target, outputFile, Collections.singleton[String](sourcePath))
          }
          catch {
            case e: IOException => context.processMessage(new CompilerMessage(compilerName, e))
          }
        }
      }
      if (!isTemp && isClassFile && !Utils.errorsDetected(context)) {
        try {
          val reader: ClassReader = new ClassReader(content.getBuffer, content.getOffset, content.getLength)
          mappingsCallback.associate(FileUtil.toSystemIndependentName(outputFile.getPath), sourcePath, reader)
        }
        catch {
          case e: Throwable =>
            val message: String = "Class dependency information may be incomplete! Error parsing generated class " + outputFile.getPath
            context.processMessage(
              new CompilerMessage(compilerName, BuildMessage.Kind.WARNING, message + "\n" + CompilerMessage.getTextFromThrowable(e), sourcePath))
        }
      }
    }

    if (isClassFile && !isTemp && source != null)
      tempSuccessfullyCompiled += source
  }

  //add source to successfullyCompiled only after the whole file is processed
  def processed(source: File): Unit = {
    if (tempSuccessfullyCompiled(source)) {
      successfullyCompiled += source
      tempSuccessfullyCompiled -= source
    }
  }
}