package org.jetbrains.jps.incremental.scala
package local

import java.io.{File, IOException}
import java.util.Collections

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.dependencyView.Callbacks
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.jps.incremental.scala.local.IdeClientIdea.CompilationResult
import org.jetbrains.jps.incremental.scala.local.PackageObjectsData.packageObjectClassName
import org.jetbrains.jps.incremental.scala.remote.CompileServerMeteringInfo
import org.jetbrains.jps.incremental.{CompileContext, Utils}
import org.jetbrains.org.objectweb.asm.ClassReader

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class IdeClientIdea(compilerName: String,
                    context: CompileContext,
                    chunk: ModuleChunk,
                    consumer: OutputConsumer,
                    mappingsCallback: Callbacks.Backend,
                    successfullyCompiled: mutable.Set[File],
                    packageObjectsData: PackageObjectsData)
  extends IdeClient(compilerName, context, chunk) {

  private val packageObjectsBaseClasses = ArrayBuffer[PackageObjectBaseClass]()
  private var compilationResults: Seq[CompilationResult] = List.empty

  //logic is taken from org.jetbrains.jps.incremental.java.OutputFilesSink.save
  override def generated(source: File, outputFile: File, name: String): Unit = {
    val compilationResult = CompilationResult(
      source = source,
      outputFile = outputFile,
      name = name
    )
    compilationResults = compilationResult +: compilationResults
  }

  override def compilationEnd(sources: Predef.Set[File]): Unit = {
    compilationResults.foreach(handleCompilationResult)
    persistPackageObjectData()
    super.compilationEnd(sources)
  }

  override def worksheetOutput(text: String): Unit = ()

  override def processingEnd(): Unit = ()

  override def sourceStarted(source: String): Unit = ()

  private def handleCompilationResult(compilationResult: CompilationResult): Unit = {
    val CompilationResult(source, outputFile, name) = compilationResult
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
            val sourcePaths = Collections.singleton(sourcePath)
            if (isClassFile) {
              consumer.registerCompiledClass(rootDescriptor.target, compiledClass)
              ClassFileUtils.correspondingTastyFile(outputFile).foreach { tastyFile =>
                consumer.registerOutputFile(rootDescriptor.target, tastyFile, sourcePaths)
              }
            } else {
              consumer.registerOutputFile(rootDescriptor.target, outputFile, sourcePaths)
            }
          }
          catch {
            case e: IOException => context.processMessage(CompilerMessage.createInternalBuilderError(compilerName, e))
          }
        }
      }
      if (!isTemp && isClassFile && !Utils.errorsDetected(context)) {
        try {
          val reader: ClassReader = new ClassReader(content.getBuffer, content.getOffset, content.getLength)
          mappingsCallback.associate(FileUtil.toSystemIndependentName(outputFile.getPath), sourcePath, reader)
          handlePackageObject(source, outputFile, reader)
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
      successfullyCompiled += source
  }

  private def handlePackageObject(source: File, outputFile: File, reader: ClassReader): Any = {
    if (outputFile.getName == s"$packageObjectClassName.class") {
      packageObjectsBaseClasses ++= collectPackageObjectBaseClasses(source, reader)
    }
  }

  private def collectPackageObjectBaseClasses(source: File, reader: ClassReader): Seq[PackageObjectBaseClass] = {
    val baseTypes: Seq[String] = {
      val superClass = Option(reader.getSuperName).filterNot(_ == "java/lang/Object")
      val interfaces = reader.getInterfaces.toSeq
      interfaces ++ superClass
    }
    val className = reader.getClassName
    val packageName = className.stripSuffix(packageObjectClassName).replace("/", ".")
    for {
      typeName <- baseTypes.map(_.replace('/', '.'))
      packObjectBaseClass = PackageObjectBaseClass(source, packageName, typeName)
      if !packageObjectsBaseClasses.contains(packObjectBaseClass)
    } yield {
      packObjectBaseClass
    }
  }

  private def persistPackageObjectData(): Unit = {
    val compiledClasses = consumer.getCompiledClasses

    for {
      item <- packageObjectsBaseClasses
      cc <- Option(compiledClasses.get(item.baseClassName))
      className <- Option(cc.getClassName) if className.startsWith(item.packageName)
      source <- cc.getSourceFiles.asScala
    } {
      packageObjectsData.add(source, item.packObjectSrc)
    }

    packageObjectsData.save(context)
  }

  private case class PackageObjectBaseClass(packObjectSrc: File, packageName: String, baseClassName: String)

}

object IdeClientIdea {

  private case class CompilationResult(source: File, outputFile: File, name: String)
}

