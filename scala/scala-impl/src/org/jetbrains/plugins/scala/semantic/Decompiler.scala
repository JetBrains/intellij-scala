package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.util.ScalaPluginJars

import java.net.URLClassLoader
import scala.language.reflectiveCalls

trait Decompiler {
  def decompile(fileName: String, contents: Array[Byte]): String
}

object Decompiler {
  private val CompilerVersion = "3.7.4"

  def apply(classpath: Seq[String], classLoader: ClassLoader): Decompiler = {
    val decompilerClass = classLoader.loadClass("org.jetbrains.plugins.scala.semantic.DecompilerImpl")
    val constructor = decompilerClass.getConstructor(classOf[Array[String]])
    //noinspection TypeAnnotation
    val decompiler = constructor.newInstance(classpath.toArray).asInstanceOf[ { def decompile(fileName: String, contents: Array[Byte]): String } ]

    (fileName: String, contents: Array[Byte]) => decompiler.decompile(fileName, contents)
  }

  /**
   * @param parent With scala-library.jar & scala3-library.jar
   */
  def classLoader(parent: ClassLoader): ClassLoader = {
    val compilerArtifacts = Seq(
      "org.scala-lang" % "scala3-compiler_3" % CompilerVersion,
      "org.scala-lang" % "scala3-interfaces" % CompilerVersion)
    val compilerJars = DependencyManager.resolve(compilerArtifacts: _*).map(_.file)
    val jars = compilerJars :+ ScalaPluginJars.semanticDecompiler
    new URLClassLoader(jars.map(_.toUri.toURL).toArray, parent)
  }
}
