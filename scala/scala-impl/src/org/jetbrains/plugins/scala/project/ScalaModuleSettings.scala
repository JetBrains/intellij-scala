package org.jetbrains.plugins.scala.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{OrderEnumerator, OrderRootType, libraries}
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.io.JarUtil.{containsEntry, getJarAttribute}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.CommonProcessors.FindProcessor
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.caches.cached
import org.jetbrains.plugins.scala.project.ScalaFeatures.SerializableScalaFeatures
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel._
import org.jetbrains.plugins.scala.project.ScalaModuleSettings._
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.sbt.project.SbtVersionProvider

import java.io.File
import java.util.jar.Attributes

private class ScalaModuleSettings private(
  module: Module,
  isBuildModule: Boolean,
  val scalaVersionProvider: ScalaVersionProvider
) {

  val scalaSdk: Option[LibraryEx] = scalaVersionProvider match {
    case ScalaVersionProvider.FromScalaSdk(library) => Some(library)
    case _ => None
  }
  val compilerVersion: Option[String] = scalaVersionProvider.compilerVersion

  def scalaMinorVersion: Option[ScalaVersion] =
    compilerVersion.flatMap(ScalaVersion.fromString)

  val scalaLanguageLevel: ScalaLanguageLevel = scalaVersionProvider.languageLevel

  val sbtVersion: Option[Version] = {
    val versionString = SbtVersionProvider.getSbtVersion(module)
    versionString.map(Version.apply)
  }

  val settingsForHighlighting: Seq[ScalaCompilerSettings] =
    ScalaCompilerConfiguration.instanceIn(module.getProject).settingsForHighlighting(module)

  val compilerPlugins: Set[String] = settingsForHighlighting.flatMap(_.plugins).toSet

  val additionalCompilerOptions: Set[String] =
    settingsForHighlighting.flatMap(_.additionalCompilerOptions).toSet ++
      (if (isBuildModule) implicitSbtCompilerOptions else Nil)

  private def implicitSbtCompilerOptions: Set[String] = {
    if (sbtVersion.exists(_ >= Version("1.6"))) {
      //Since sbt 1.6 `-Xsource:3` compiler option for  build definition scala files
      //(see https://github.com/sbt/sbt/pull/6664/files)
      //note: sbt also adds "-Wconf:cat=unused-nowarn:s" but seems that it's not important for us
      Set("-Xsource:3")
    }
    else Set.empty
  }

  val isMetaEnabled: Boolean =
    compilerPlugins.exists(isMetaParadiseJar)

  val hasScala3: Boolean = scalaLanguageLevel.isScala3

  val hasNewCollectionsFramework: Boolean = scalaLanguageLevel >= Scala_2_13

  val isIdBindingEnabled: Boolean = scalaLanguageLevel >= Scala_2_12

  //plugin example:
  // ~/Coursier/cache/v1/https/repo1.maven.org/maven2/org/scala-js/scalajs-compiler_2.13.6/1.7.1/scalajs-compiler_2.13.6-1.7.1.jar
  val isScalaJs: Boolean = compilerPlugins.exists(p => p.contains("scalajs") || p.contains("scala-js")) ||
    //Scala 3 relies on the compiler flag
    additionalCompilerOptions.contains("-scalajs")

  //plugin example:
  //~/Coursier/cache/v1/https/repo1.maven.org/maven2/org/scala-native/nscplugin_3.2.1/0.4./nscplugin_3.2.1-0.4.7.jar
  val isScalaNative: Boolean =
    compilerPlugins.exists(p => p.contains("scala-native") || p.contains("nscplugin"))

  val isTrailingCommasEnabled: Boolean = {
    val version = compilerVersion.map(Version.apply)
    val `is scala 2.12.2` = version.exists(_ >= Scala_2_12_2_version)
    `is scala 2.12.2`
  }

  val literalTypesEnabled: Boolean = scalaLanguageLevel >= ScalaLanguageLevel.Scala_2_13 ||
    additionalCompilerOptions.contains("-Yliteral-types")

  val kindProjectorPlugin: Option[String] =
    compilerPlugins.find(_.contains("kind-projector"))

  def kindProjectorUnderscorePlaceholdersEnabled: Boolean =
    additionalCompilerOptions.contains("-P:kind-projector:underscore-placeholders")

  def YKindProjectorOptionEnabled: Boolean =
    additionalCompilerOptions.exists(_.startsWith("-Ykind-projector"))

  def YKindProjectorUnderscoresOptionEnabled: Boolean =
    additionalCompilerOptions.contains("-Ykind-projector:underscores")

  val betterMonadicForPluginEnabled: Boolean =
    compilerPlugins.exists(_.contains("better-monadic-for"))

  val contextAppliedPluginEnabled: Boolean =
    compilerPlugins.exists(_.contains("context-applied"))

  /**
   * Should we check if it's a Single Abstract Method?
   * In 2.11 works with -Xexperimental
   * In 2.12 works by default
   *
   * @return true if language level and flags are correct
   */
  val isSAMEnabled: Boolean = scalaLanguageLevel match {
    case lang if lang > Scala_2_11 => true // if scalaLanguageLevel is None, we treat it as Scala 2.12
    case lang if lang == Scala_2_11 =>
      settingsForHighlighting.exists { settings =>
        settings.experimental || settings.additionalCompilerOptions.contains("-Xexperimental")
      }
    case _ => false
  }

  val hasSource3Flag: Boolean =
    additionalCompilerOptions.contains("-Xsource:3")

  val hasSource3CrossFlag: Boolean =
    additionalCompilerOptions.contains("-Xsource:3-cross")

  val hasSourceFutureFlag: Boolean =
    additionalCompilerOptions.contains("-source:future") || additionalCompilerOptions.contains("--source:future")

  val hasDeprecationFlag: Boolean =
    additionalCompilerOptions.contains("-deprecation") || additionalCompilerOptions.contains("--deprecation")

  val isPartialUnificationEnabled: Boolean =
    scalaLanguageLevel >= Scala_2_13 || additionalCompilerOptions.contains("-Ypartial-unification")

  val hasNoIndentFlag: Boolean = additionalCompilerOptions.contains("-no-indent")
  val hasOldSyntaxFlag: Boolean = additionalCompilerOptions.contains("-old-syntax")

  val isCompilerStrictMode: Boolean =
    settingsForHighlighting.exists(_.strict)

  val customDefaultImports: Option[Seq[String]] =
    additionalCompilerOptions.collectFirst {
      case Yimports(imports) if scalaLanguageLevel >= Scala_2_13 => imports
      case YnoPredefOrNoImports(imports)                         => imports
    }

  def XSourceFlag: ScalaXSourceFlag =
    if (hasSource3Flag)           ScalaXSourceFlag.XSource3
    else if (hasSource3CrossFlag) ScalaXSourceFlag.XSource3Cross
    else                          ScalaXSourceFlag.None

  val features: SerializableScalaFeatures =
    ScalaFeatures(
      scalaMinorVersion.getOrElse(ScalaVersion.default),
      XSourceFlag,
      hasNoIndentFlag = hasNoIndentFlag,
      hasOldSyntaxFlag = hasOldSyntaxFlag,
      hasDeprecationFlag = hasDeprecationFlag,
      hasSourceFutureFlag = hasSourceFutureFlag,
      hasMetaEnabled = isMetaEnabled,
      hasTrailingCommasEnabled = isTrailingCommasEnabled,
      hasUnderscoreWildcardsDisabled = kindProjectorUnderscorePlaceholdersEnabled || YKindProjectorUnderscoresOptionEnabled,
    )
}

private object ScalaModuleSettings {
  private val Scala_2_12_2_version = Version("2.12.2")

  sealed trait ScalaVersionProvider {
    def languageLevel: ScalaLanguageLevel
    def compilerVersion: Option[String]
  }
  object ScalaVersionProvider {
    case class FromScalaSdk(library: LibraryEx) extends ScalaVersionProvider {
      override def languageLevel: ScalaLanguageLevel = library.properties.languageLevel
      override def compilerVersion: Option[String] = library.libraryVersion
    }
    case class Explicit(languageLevel: ScalaLanguageLevel, compilerVersion: Option[String]) extends ScalaVersionProvider

    def fromFullVersion(scalaVersion: String): Explicit = {
      val languageLevel = ScalaLanguageLevel.findByVersion(scalaVersion).getOrElse(ScalaLanguageLevel.getDefault)
      ScalaVersionProvider.Explicit(languageLevel, Some(scalaVersion))
    }
  }

  def apply(module: Module): Option[ScalaModuleSettings] = {
    if (module.isBuildModule) {
      // build module doesn't have Scala SDK
      forSbtBuildModule(module)
    }
    else {
      val processor: FindProcessor[libraries.Library] = _.isScalaSdk
      OrderEnumerator.orderEntries(module)
        .librariesOnly
        .forEachLibrary(processor)
      val scalaSdk = processor.getFoundValue.asInstanceOf[LibraryEx]

      Option(scalaSdk)
        .map(ScalaVersionProvider.FromScalaSdk)
        .map(new ScalaModuleSettings(module, isBuildModule = false, _))
    }
  }

  //TODO: instead of relying of some classpath, just register module-level Scala SDK for `-build` modules
  // the same way as we do for normal modules
  private def forSbtBuildModule(module: Module): Option[ScalaModuleSettings] =
    for {
      libraryWithRuntimeJar <- findLibraryWithScalaRuntime(module)
      scalaVersion <- detectScalaVersionFromJars(libraryWithRuntimeJar)
    } yield {
      val versionProvider = ScalaVersionProvider.fromFullVersion(scalaVersion)
      new ScalaModuleSettings(module, isBuildModule = true, versionProvider)
    }

  /**
   * For a given build module returns library which contains scala-library jar
   *
   * Note:
   *  - in SBT projects `-build` module contains single module-level library in format "sbt: sbt-1.8.2"
   *  - in BSP/SBT projects `-build` module contains many libraries for each jar
   *    However it still can contain some libraries which also contain `: sbt-` (example: "BSP: ivy-2.3.0-sbt-a8f9eb5bf09d0539ea3658a2c2d4e09755b5133e")
   *    So we first handle BSP case and search "BSP: scala-library-2.12.17" and only then search for library which contains "sbt-1.8.2"
   */
  private def findLibraryWithScalaRuntime(module: Module): Option[Library] = {
    val processor1: FindProcessor[libraries.Library] = { lib =>
      LibraryExt.isRuntimeLibrary(lib.getName)
    }
    val processor2: FindProcessor[libraries.Library] = { lib =>
      //example: "sbt: sbt-1.8.2"
      lib.getName.contains(": " + org.jetbrains.sbt.Sbt.BuildLibraryPrefix)
    }

    val librariesEnumerator = OrderEnumerator.orderEntries(module).librariesOnly
    librariesEnumerator.forEachLibrary(processor1)
    val result = Option(processor1.getFoundValue).orElse {
      librariesEnumerator.forEachLibrary(processor2)
      Option(processor2.getFoundValue)
    }
    result
  }

  private val LibraryVersionReg = "\\d+\\.\\d+\\.\\d+".r

  // Example of scala lib path in sbt 1.3.13 classpath:
  // ~/.sbt/boot/scala-2.12.10/lib/scala-library.jar
  private def detectScalaVersionFromJars(library: Library): Option[String] = {
    val classpath = library.getFiles(OrderRootType.CLASSES): Array[VirtualFile]
    val scalaLibraryJar = classpath.find(f => LibraryExt.isRuntimeLibrary(f.getName))
    scalaLibraryJar.map(_.getPath).flatMap(LibraryVersionReg.findFirstIn)
  }

  private object Yimports {
    private val YimportsPrefix = "-Yimports:"

    def unapply(setting: String): Option[Seq[String]] =
      if (setting.startsWith(YimportsPrefix))
        Option(setting.substring(YimportsPrefix.length).split(",").map(_.trim).toSeq)
      else None
  }

  private object YnoPredefOrNoImports {
    private val Ynopredef  = "-Yno-predef"
    private val Ynoimports = "-Yno-imports"

    private val importSettingsPrefixes = Seq(Ynopredef, Ynoimports)

    def unapply(setting: String): Option[Seq[String]] = {
      val prefix = importSettingsPrefixes.find(setting.startsWith)

      prefix.collect {
        case Ynopredef  => Seq("java.lang", "scala")
        case Ynoimports => Seq.empty
      }
    }
  }

  private val isMetaParadiseJar = cached("isMetaParadiseJar", ModificationTracker.NEVER_CHANGED, (pathname: String) => {
    new File(pathname) match {
      case file if containsEntry(file, "scalac-plugin.xml") =>
        def hasAttribute(nameSuffix: String, value: String) = getJarAttribute(
          file,
          new Attributes.Name(s"Specification-$nameSuffix")
        ) == value

        hasAttribute("Vendor", "org.scalameta") &&
          hasAttribute("Title", "paradise")
      case _ => false
    }
  })

}

