package org.jetbrains.jps.incremental.scala
package local

import java.io.{BufferedInputStream, File, FileInputStream}
import java.util
import java.util.zip.{ZipEntry, ZipInputStream}

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.builders.{BuildRootDescriptor, BuildTarget}
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.{BinaryContent, CompileContext, CompiledClass, FSOperations}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import sbt.internal.inc.JarUtils

import scala.collection.JavaConverters._
import scala.util.control.Exception._


/**
 * Nikolay.Tropin
 * 11/18/13
 */
class IdeClientSbt(compilerName: String,
                   context: CompileContext,
                   modules: Seq[String],
                   consumer: OutputConsumer,
                   sourceToTarget: File => Option[BuildTarget[_ <: BuildRootDescriptor]])
        extends IdeClient(compilerName, context, modules, consumer) {

  override def allGenerated(generated: Array[(File, Array[(File, String)])]): Unit = {

    case class GeneratedClass(source: File, binary: File, name: String) {
      lazy val pathInJar: String = JarUtils.ClassInJar.fromFile(binary).toClassFilePath
    }

    def resolveOutputJar(generatedClasses: Seq[GeneratedClass]) = {
      generatedClasses
        .headOption
        .map(_.binary)
        .filter(JarUtils.isClassInJar)
        .map(classFile => JarUtils.ClassInJar.fromFile(classFile).splitJarReference._1)
    }

    def prepareLazyLoadedClassBinaryContent(generatedClasses: Seq[GeneratedClass]): Unit = {
      generatedClasses.foreach {
        case GeneratedClass(sourceFile, classFile, className) =>
          this.generated(sourceFile, classFile, className)
      }
    }

    def prefetchClassBinaryContentFromJar(jarFile: File, generatedClasses: Seq[GeneratedClass]): Unit = {
      val binaryContentForClasses = loadBinaryContent(jarFile, generatedClasses)
      for {
        cls <- generatedClasses
        binaryContent <- binaryContentForClasses.get(cls.pathInJar)
      } {
        invalidateBoundForms(cls.source)
        val compiledClass = new CompiledClass(cls.binary, cls.source, cls.name, binaryContent)
        register(compiledClass)
      }
    }

    def loadBinaryContent(jarFile: File, generatedClasses: Seq[GeneratedClass]) = {
      def withJarInputStream[A](jar: File)(action: ZipInputStream => A): A = {
        val stream = new ZipInputStream(new BufferedInputStream(new FileInputStream(jar)))
        try action(stream) finally stream.close()
      }

      def entries(stream: ZipInputStream): Iterator[ZipEntry] = {
        Iterator.continually(stream.getNextEntry).takeWhile(_ != null)
      }

      withJarInputStream(jarFile) { stream =>
        val neededClassFiles: Set[String] = generatedClasses.map(_.pathInJar)(collection.breakOut)
        entries(stream).flatMap { entry =>
          val name = entry.getName
          if (neededClassFiles.contains(name)) {
            val content = new BinaryContent(FileUtil.loadBytes(stream))
            Some(name -> content)
          } else {
            None
          }
        }.toMap
      }
    }

    val generatedClasses = for {
      (sourceFile, binaries) <- generated
      (classFile, className) <- binaries
    } yield GeneratedClass(sourceFile, classFile, className)

    resolveOutputJar(generatedClasses) match {
      case Some(jarFile) =>
        prefetchClassBinaryContentFromJar(jarFile, generatedClasses)
      case None =>
        prepareLazyLoadedClassBinaryContent(generatedClasses)
    }
  }

  def generated(sourceFile: File, classFile: File, className: String) {
    invalidateBoundForms(sourceFile)

    val compiledClass =
      if (JarUtils.isClassInJar(classFile)) {
        new JaredLazyCompiledClass(classFile, sourceFile, className)
      } else {
        new LazyCompiledClass(classFile, sourceFile, className)
      }

    register(compiledClass)
  }

  private def register(compiledClass: CompiledClass): Unit = {
    val sourceFile = compiledClass.getSourceFile
    val target = sourceToTarget(sourceFile).getOrElse {
      throw new RuntimeException("Unknown source file: " + sourceFile)
    }
    consumer.registerCompiledClass(target, compiledClass)
  }

  def processed(source: File): Unit = {}

  // TODO Expect JPS compiler in UI-designer to take generated class events into account
  private val FormsToCompileKey = catching(classOf[ClassNotFoundException], classOf[NoSuchFieldException]).opt {
    val field = Class.forName("org.jetbrains.jps.uiDesigner.compiler.FormsBuilder").getDeclaredField("FORMS_TO_COMPILE")
    field.setAccessible(true)
    field.get(null).asInstanceOf[Key[util.Map[File, util.Collection[File]]]]
  }

  private def invalidateBoundForms(source: File) {
    FormsToCompileKey.foreach { key =>
      val boundForms: Option[Iterable[File]] = {
        val sourceToForm = context.getProjectDescriptor.dataManager.getSourceToFormMap
        val sourcePath = FileUtil.toCanonicalPath(source.getPath)
        Option(sourceToForm.getState(sourcePath)).map(_.asScala.map(new File(_)))
      }

      boundForms.foreach { forms =>
        val formsToCompile = Option(key.get(context)).getOrElse(new util.HashMap[File, util.Collection[File]]())
        formsToCompile.put(source, forms.toVector.asJava)
        key.set(context, formsToCompile)
      }
    }
  }

  override def sourceStarted(source: String): Unit = {
    FSOperations.markDirty(context, CompilationRound.NEXT, new File(source))
  }
}