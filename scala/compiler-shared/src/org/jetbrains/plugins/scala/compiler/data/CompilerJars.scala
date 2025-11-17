package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.containsScala3

import java.nio.file.Path

/**
 * @param libraryJars             scala-library.jar, for scala3 projects also contains scala3-library_3.jar
 * @param compilerJars            jar files required to instantiate scala compiler '''NOTE: doesn't include library files'''<br>
 * @param compilerJar             scala-compiler or scala3-compiler_3 jar
 * @param customCompilerBridgeJar in case it's None, a bundled compiler bridge will be used<br>
 *                                (see `org.jetbrains.jps.incremental.scala.local.CompilerFactoryImpl.getScalac`)
 * @see sbt.internal.inc.ScalaInstance<br>
 *      https://github.com/sbt/zinc/pull/960
 */
case class CompilerJars(
  libraryJars: Seq[Path],
  compilerJars: Seq[Path],
  compilerJar: Path,
  customCompilerBridgeJar: Option[Path],
  replClasspath: Seq[Path]
) {

  lazy val hasScala3: Boolean =
    containsScala3(allJars)

  def allJars: Seq[Path] =
    libraryJars ++ compilerJars
}
