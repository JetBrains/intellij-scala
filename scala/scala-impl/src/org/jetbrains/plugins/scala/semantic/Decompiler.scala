package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.jetbrains.plugins.scala.{BuildInfo, DependencyManager}

import java.net.URLClassLoader
import scala.reflect.Selectable.reflectiveSelectable

trait Decompiler {
  def decompile(fileName: String, contents: Seq[Byte]): String
}

object Decompiler {
  private val CompilerVersion = BuildInfo.scala3Version

  def apply(classpath: Seq[String], classLoader: ClassLoader): Decompiler = {
    val decompilerClass = classLoader.loadClass("org.jetbrains.plugins.scala.semantic.DecompilerImpl")
    val constructor = decompilerClass.getConstructor(classOf[Array[String]])
    //noinspection TypeAnnotation
    val decompiler = constructor.newInstance(classpath.toArray).asInstanceOf[ { def decompile(fileName: String, contents: Seq[Byte]): String } ]

    (fileName: String, contents: Seq[Byte]) => decompiler.decompile(fileName, contents)
  }

  /**
   * @param parent With scala-library.jar & scala3-library.jar
   */
  def classLoader(parent: ClassLoader): ClassLoader = {
    val compiler = ("org.scala-lang" % "scala3-compiler_3" % CompilerVersion).transitive()
    val ScalaLibraryIds = Set("scala3-library_3", "scala-library")
    val compilerJars = DependencyManager.resolve(compiler).filterNot(d => ScalaLibraryIds(d.info.artId)).map(_.file)
    val jars = compilerJars :+ ScalaPluginJars.semanticDecompiler
    new URLClassLoader(jars.map(_.toUri.toURL).toArray, parent)
  }
}
