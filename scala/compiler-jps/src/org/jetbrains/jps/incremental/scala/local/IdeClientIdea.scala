package org.jetbrains.jps.incremental.scala
package local

import java.io.{File, IOException}
import java.util.Collections

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.builders.java.dependencyView.Callbacks
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.jps.incremental.scala.local.PackageObjectsData.packageObjectClassName
import org.jetbrains.jps.incremental.{CompileContext, Utils}
import org.jetbrains.org.objectweb.asm.ClassReader

import scala.collection._
import scala.collection.mutable.ArrayBuffer

/**
  * Nikolay.Tropin
  * 11/18/13
  */
class IdeClientIdea(compilerName: String,
                    context: CompileContext,
                    modules: Seq[String],
                    consumer: OutputConsumer,
                    mappingsCallback: Callbacks.Backend,
                    successfullyCompiled: mutable.Set[File],
                    packageObjectsData: PackageObjectsData)
  extends IdeClient(compilerName, context, modules, consumer) {

  private val tempSuccessfullyCompiled = mutable.Set[File]()
  private val packageObjectsBaseClasses = ArrayBuffer[PackageObjectBaseClass]()

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
      tempSuccessfullyCompiled += source
  }

  //add source to successfullyCompiled only after the whole file is processed
  def processed(source: File): Unit = {
    if (tempSuccessfullyCompiled(source)) {
      successfullyCompiled += source
      tempSuccessfullyCompiled -= source
    }
  }

  override def compilationEnd(): Unit = {
    persistPackageObjectData()
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

    for (item <- packageObjectsBaseClasses;
         cc <- Option(compiledClasses.get(item.baseClassName));
         className <- Option(cc.getClassName) if className.startsWith(item.packageName)) {

      packageObjectsData.add(cc.getSourceFile, item.packObjectSrc)
    }

    packageObjectsData.save(context)
  }

  private case class PackageObjectBaseClass(packObjectSrc: File, packageName: String, baseClassName: String)

}

