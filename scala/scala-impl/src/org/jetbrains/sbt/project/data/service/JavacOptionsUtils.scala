package org.jetbrains.sbt.project.data.service

import com.intellij.pom.java.LanguageLevel

/** javac options reference [[https://docs.oracle.com/en/java/javase/11/tools/javac.htm]] */
object JavacOptionsUtils {

  def javaLanguageLevel(javacOptions: Seq[String]): Option[LanguageLevel] =
    for {
      sourceValue <- JavacOptionsUtils.effectiveSourceValue(javacOptions)
      languageLevel <- Option(LanguageLevel.parse(sourceValue))
    } yield languageLevel

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

  /**
   * Assuming that:
   *  - effective `-target` value is already saved in CompilerConfiguration
   *  - effective `-source` value is already saved in project of module setting
   *  - `--release` version is controlled explicitly by idea, see <br>
   *    File | Settings | Build, Execution, Deployment | Compiler | Java Compiler | Use '--release' option for cross compilation
   */
  def withoutSourceTargetReleaseOptions(javacOptions: Seq[String]): Seq[String] =
    javacOptions
      .removePair("-target")
      .removePair("-source")
      .removePair("--release")

  private implicit class SeqOps(private val options: Seq[String]) extends AnyVal {

    def removePair(name: String): Seq[String] = {
      val index = options.indexOf(name)

      if (index == -1) options
      else {
        val (prefix, suffix) = options.splitAt(index)
        prefix ++ suffix.drop(2)
      }
    }
  }
}
