package org.jetbrains.sbt.project.data.service

import com.intellij.pom.java.LanguageLevel

/** javac options reference [[https://docs.oracle.com/en/java/javase/11/tools/javac.htm]] */
private object JavacOptionsUtils {

  private val Target        = "-target"
  private val Source        = "-source"
  private val Release       = "--release"
  private val EnablePreview = "--enable-preview"

  private val ExplicitlyHandledKeyValueOptions = Set(Target, Source, Release)
  private val ExplicitlyHandledFLagOptions = Set(EnablePreview)

  /**
   * Assuming that:
   *  - `-target` effective value is already saved in CompilerConfiguration
   *  - `-source` effective value is already saved in module language level settings
   *  - `--enable-preview` is handled along with `-source` or `-release` option to set proper language level
   *  - `--release` version is controlled explicitly by idea, see <br>
   *    File | Settings | Build, Execution, Deployment | Compiler | Java Compiler | Use '--release' option for cross compilation
   */
  def withoutExplicitlyHandledOptions(javacOptions: Seq[String]): Seq[String] = {
    val res1 = ExplicitlyHandledKeyValueOptions.foldLeft(javacOptions)(_.removePair(_))
    val res2 = res1.filterNot(ExplicitlyHandledFLagOptions.contains)
    res2
  }

  def javaLanguageLevel(javacOptions: Seq[String]): Option[LanguageLevel] =
    for {
      sourceValue <- JavacOptionsUtils.effectiveSourceValue(javacOptions)
      languageLevel <- Option(LanguageLevel.parse(sourceValue))
    } yield {
      val enablePreview = javacOptions.contains(EnablePreview)
      val maybePreview = if (enablePreview) Option(languageLevel.getPreviewLevel) else None
      maybePreview.getOrElse(languageLevel)
    }

  private def effectiveSourceValue(javacOptions: Seq[String]): Option[String] = {
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
  private def releaseValue(javacOptions: Seq[String]): Option[String] =
    value(javacOptions, Release) // yes, release uses double dash "--"

  private def sourceValue(javacOptions: Seq[String]): Option[String] =
    value(javacOptions, Source)

  private def targetValue(javacOptions: Seq[String]): Option[String] =
    value(javacOptions, Target)

  private def value(options: Seq[String], key: String): Option[String] =
    for {
      keyPos <- Option(options.indexOf(key)).filterNot(_ == -1)
      value <- options.lift(keyPos + 1)
    } yield value

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
