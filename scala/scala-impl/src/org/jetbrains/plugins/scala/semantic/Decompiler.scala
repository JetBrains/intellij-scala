package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.util.ScalaPluginJars

import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader

trait Decompiler {
  def decompile(fileName: String, contents: Array[Byte]): String
}

object Decompiler {
  private val CompilerVersion = "3.7.4"

  def apply(classpath: Seq[String], classLoader: ClassLoader): Decompiler = {
    val decompilerClass = classLoader.loadClass("org.jetbrains.plugins.scala.semantic.DecompilerImpl")
    val constructor = decompilerClass.getConstructor(classOf[Array[String]])
    // The compiler needs its own standard library's internal annotations, even when reading older TASTy.
    val decompilerClasspath = ScalaPluginJars.scalaLibraryJar.toString +: classpath
    val decompiler = constructor.newInstance(decompilerClasspath.toArray)
    // Scala 3.8.4 crashes when pickling a structural call with an Array[Byte] parameter.
    val decompileMethod = decompilerClass.getMethod("decompile", classOf[String], classOf[Array[Byte]])

    (fileName: String, contents: Array[Byte]) =>
      try decompileMethod.invoke(decompiler, fileName, contents).asInstanceOf[String]
      catch {
        case e: InvocationTargetException => throw e.getCause
      }
  }

  /**
   * @param parent With scala-library.jar & scala3-library.jar
   */
  def classLoader(parent: ClassLoader): ClassLoader = {
    val compiler = ("org.scala-lang" % "scala3-compiler_3" % CompilerVersion).transitive()
    val compilerJars = DependencyManager.resolve(compiler).map(_.file)
    val jars = compilerJars :+ ScalaPluginJars.semanticDecompiler
    new URLClassLoader(jars.map(_.toUri.toURL).toArray, parent)
  }
}
