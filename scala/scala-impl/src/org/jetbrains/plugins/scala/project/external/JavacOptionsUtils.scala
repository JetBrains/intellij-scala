package org.jetbrains.plugins.scala.project.external

/** javac options reference [[https://docs.oracle.com/en/java/javase/11/tools/javac.htm]] */
object JavacOptionsUtils {

  def effectiveSourceValue(javacOptions: Seq[String]): Option[String] = {
    val release = releaseValue(javacOptions)
    release.orElse(sourceValue(javacOptions))
  }

  def effectiveTargetValue(javacOptions: Seq[String]): Option[String] = {
    val release = releaseValue(javacOptions)
    release.orElse(targetValue(javacOptions))
  }

  /**
   * For what is "--release" option see:
   *  - [[http://openjdk.java.net/jeps/247 JEP 247: Compile for Older Platform Versions]]
   *  - [[https://stackoverflow.com/questions/43102787/what-is-the-release-flag-in-the-java-9-compiler/43103038#43103038 What is the --release flag in the Java 9 compiler?]]
   */
  def releaseValue(javacOptions: Seq[String]): Option[String] =
    value(javacOptions, "--release") // yes, release uses double dash "--"

  def sourceValue(javacOptions: Seq[String]): Option[String] =
    value(javacOptions, "-source")

  def targetValue(javacOptions: Seq[String]): Option[String] =
    value(javacOptions, "-target")

  def value(options: Seq[String], key: String): Option[String] =
    for {
      keyPos <- Option(options.indexOf(key)).filterNot(_ == -1)
      value <- options.lift(keyPos + 1)
    } yield value
}
