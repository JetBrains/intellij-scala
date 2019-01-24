package org.jetbrains.jps.incremental.scala
package data

import java.io.File

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala._
import org.jetbrains.jps.incremental.scala.model.{IncrementalityType, LibrarySettings}
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
case class CompilerData(compilerJars: Option[CompilerJars], javaHome: Option[File], incrementalType: IncrementalityType)

object CompilerData extends CompilerDataFactory {
  private val Log: JpsLogger = JpsLogger.getInstance(CompilerData.getClass.getName)

  override def from(context: CompileContext, chunk: ModuleChunk): Either[String, CompilerData] = {
    val project = context.getProjectDescriptor
    val target = chunk.representativeTarget
    val module = target.getModule

    val compilerJars = if (InitialScalaBuilder.hasScala(context, module)) {
      compilerJarsIn(context, module).flatMap { jars: CompilerJars =>
        val absentJars = jars.files.filter(!_.exists)
        Either.cond(absentJars.isEmpty,
          Some(jars),
          "Scala compiler JARs not found (module '" + chunk.representativeTarget().getModule.getName + "'): "
                  + absentJars.map(_.getPath).mkString(", "))
      }
    } else {
      Right(None)
    }

    compilerJars.flatMap { jars =>
      val incrementalityType = SettingsManager.getProjectSettings(project.getProject).getIncrementalityType
      javaHome(context, module).map(CompilerData(jars, _, incrementalityType))
    }

  }

  def javaHome(context: CompileContext, module: JpsModule): Either[String, Option[File]] = {
    val project = context.getProjectDescriptor
    val model = project.getModel

    Option(module.getSdk(JpsJavaSdkType.INSTANCE))
            .toRight("No JDK in module " + module.getName)
            .flatMap { moduleJdk =>

      val globalSettings = SettingsManager.getGlobalSettings(model.getGlobal)

      val jvmSdk = if (ScalaBuilder.isCompileServerEnabled(context)) {
        Option(globalSettings.getCompileServerSdk).flatMap { sdkName =>
          val libraries = model.getGlobal.getLibraryCollection.getLibraries(JpsJavaSdkType.INSTANCE).asScala
          libraries.find(_.getName == sdkName).map(_.getProperties)
        }
      } else {
        Option(model.getProject.getSdkReferencesTable.getSdkReference(JpsJavaSdkType.INSTANCE))
                .flatMap(references => Option(references.resolve)).map(_.getProperties)
      }

      if (jvmSdk.contains(moduleJdk)) Right(None)
      else {
        val directory = new File(moduleJdk.getHomePath)
        Either.cond(directory.exists, Some(directory), "JDK home directory does not exists: " + directory)
      }
    }
  }

  def isDottyModule(context: CompileContext, module: JpsModule): Boolean = {
    compilerJarsIn(context, module) match {
      case Right(jars) => jars.dotty.isDefined
      case _ => false
    }
  }

  def needNoBootCp(context: CompileContext, chunk: ModuleChunk): Boolean = {
    chunk.getModules.asScala.forall(needNoBootCp(context, _))
  }

  def compilerVersion(context: CompileContext, module: JpsModule): Option[String] = {
    compilerJarsIn(context, module) match {
      case Right(CompilerJars(_, compiler, _)) => version(compiler)
      case Left(error) => Log.error(error)
        None
    }
  }

  private def needNoBootCp(context: CompileContext, module: JpsModule): Boolean = {
    def tooOld(version: Option[String]) = version.exists(v => v.startsWith("2.8") || v.startsWith("2.9"))

    compilerJarsIn(context, module) match {
      case Right(jars @ CompilerJars(_, compiler, _)) => jars.dotty.isEmpty && !tooOld(version(compiler))
      case _ => false
    }
  }

  def compilerJarsIn(context: CompileContext, module: JpsModule): Either[String, CompilerJars] = {
    def findJar(files: Seq[File], prefix: String, sdk: JpsLibrary): Either[String, File] = {
      find(files, prefix, suffix = ".jar").left.map(_ + " in Scala compiler classpath in Scala SDK " + sdk.getName)
    }

    for {
      sdk <- InitialScalaBuilder.scalaSdk(context, module).toRight(s"Scala SDK not found in module ${module.getName}")
      files = sdk.getProperties.asInstanceOf[LibrarySettings].getCompilerClasspath
      libraryJar <- findJar(files, "scala-library", sdk)
      compilerJar <- findJar(files, "scala-compiler", sdk)
      extraJars = files.filterNot(file => file == libraryJar || file == compilerJar)
      _ <- version(compilerJar).filter(_.startsWith("2.10")) // TODO implement a better version comparison)
             .map(_ => findJar(extraJars, "scala-reflect", sdk).map(_ => ()))
             .getOrElse(Right(()))
    } yield CompilerJars(libraryJar, compilerJar, extraJars)
  }

  def find(files: Seq[File], prefix: String, suffix: String): Either[String, File] = {
    files.filter(it => it.getName.startsWith(prefix) && it.getName.endsWith(suffix)) match {
      case Seq() =>
        Left("No '%s*%s'".format(prefix, suffix))
      case Seq(file) =>
        Right(file)
      case Seq(duplicates @ _*) =>
        Left("Multiple '%s*%s' files (%s)".format(prefix, suffix, duplicates.map(_.getName).mkString(", ")))
    }
  }

  def version(compiler: File): Option[String] = readProperty(compiler, "compiler.properties", "version.number")
}
