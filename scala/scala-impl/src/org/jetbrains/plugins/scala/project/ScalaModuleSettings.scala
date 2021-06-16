package org.jetbrains.plugins.scala.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{OrderEnumerator, OrderRootType, libraries}
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.io.JarUtil.{containsEntry, getJarAttribute}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.CommonProcessors.{CollectProcessor, FindProcessor}
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel._
import org.jetbrains.plugins.scala.project.ScalaModuleSettings.{ScalaVersionProvider, Yimports, YnoPredefOrNoImports, isMetaParadiseJar}
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.sbt.settings.SbtSettings

import java.io.File
import java.util.jar.Attributes
import scala.jdk.CollectionConverters.IteratorHasAsScala

private class ScalaModuleSettings(module: Module, val scalaVersionProvider: ScalaVersionProvider) {

  val scalaSdk: Option[LibraryEx] = scalaVersionProvider match {
    case ScalaVersionProvider.FromScalaSdk(library) => Some(library)
    case _ => None
  }
  val compilerVersion: Option[String] = scalaVersionProvider.compilerVersion

  val scalaLanguageLevel: ScalaLanguageLevel = scalaVersionProvider.languageLevel

  val settingsForHighlighting: Seq[ScalaCompilerSettings] =
    ScalaCompilerConfiguration.instanceIn(module.getProject).settingsForHighlighting(module)

  val compilerPlugins: Set[String] = settingsForHighlighting.flatMap(_.plugins).toSet

  val additionalCompilerOptions: Set[String] = settingsForHighlighting.flatMap(_.additionalCompilerOptions).toSet

  val isMetaEnabled: Boolean =
    compilerPlugins.exists(isMetaParadiseJar)

  val hasScala3: Boolean = scalaLanguageLevel >= Dotty

  val hasNewCollectionsFramework: Boolean = scalaLanguageLevel >= Scala_2_13

  val isIdBindingEnabled: Boolean = scalaLanguageLevel >= Scala_2_12

  val isScalaJs: Boolean = compilerPlugins.contains("scala-js")

  val isScalaNative: Boolean = compilerPlugins.contains("scala-native")

  val sbtVersion: Option[Version] =
    SbtSettings.getInstance(module.getProject)
      .getLinkedProjectSettings(module)
      .flatMap { projectSettings =>
        Option(projectSettings.sbtVersion)
      }.map {
      Version(_)
    }

  val isTrailingCommasEnabled: Boolean = {
    val version = compilerVersion.map(Version.apply)
    val `is scala 2.12.2` = version.exists(_ >= Version("2.12.2"))
    `is scala 2.12.2`
  }

  val literalTypesEnabled: Boolean = scalaLanguageLevel >= ScalaLanguageLevel.Scala_2_13 ||
    additionalCompilerOptions.contains("-Yliteral-types")

  val kindProjectorPlugin: Option[String] =
    compilerPlugins.find(_.contains("kind-projector"))

  def kindProjectorUnderscorePlaceholdersEnabled: Boolean =
    additionalCompilerOptions.contains("-P:kind-projector:underscore-placeholders")

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

  val isSource3Enabled: Boolean =
    additionalCompilerOptions.contains("-Xsource:3")

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
}

private object ScalaModuleSettings {

  sealed trait ScalaVersionProvider {
    def languageLevel: ScalaLanguageLevel
    def compilerVersion: Option[String]
  }
  object ScalaVersionProvider {
    case class FromScalaSdk(library: LibraryEx) extends ScalaVersionProvider {
      override def languageLevel: ScalaLanguageLevel = library.properties.languageLevel
      override def compilerVersion: Option[String] = library.compilerVersion
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
      val processor = new CollectProcessor[libraries.Library]{
        override def accept(t: Library): Boolean = t.isScalaSdk
      }

      OrderEnumerator.orderEntries(module)
        .librariesOnly
        .forEachLibrary(processor)

      // TODO: this is a workaround for SCL-17196, SCL-18166, SCL-18867
      //  (there can be 2 SDKs in Scala3 modules, if there is another Scala2 module which uses same scala2 version
      //  that is used by Scala3
      val scalaSdk: Option[LibraryEx] = processor.getResults.iterator().asScala
        .map(_.asInstanceOf[LibraryEx])
        .maxByOption(_.properties.languageLevel)

      scalaSdk
        .map(ScalaVersionProvider.FromScalaSdk)
        .map(new ScalaModuleSettings(module, _))
    }
  }

  private def forSbtBuildModule(module: Module): Option[ScalaModuleSettings] =
    for {
      sbtAndPluginsLib <- {
        val processor: FindProcessor[libraries.Library] = _.getName.endsWith(org.jetbrains.sbt.Sbt.BuildLibraryName)
        OrderEnumerator.orderEntries(module).librariesOnly.forEachLibrary(processor)
        Option(processor.getFoundValue)
      }
      scalaVersion <- scalaVersionFromForSbtClasspath(sbtAndPluginsLib)
    } yield {
      val versionProvider = ScalaVersionProvider.fromFullVersion(scalaVersion)
      new ScalaModuleSettings(module, versionProvider)
    }

  private val LibraryVersionReg = "\\d+\\.\\d+\\.\\d+".r

  // Example of scala lib path in sbt 1.3.13 classpath:
  // ~/.sbt/boot/scala-2.12.10/lib/scala-library.jar
  private def scalaVersionFromForSbtClasspath(sbtLib: Library): Option[String] = {
    val classpath = sbtLib.getFiles(OrderRootType.CLASSES): Array[VirtualFile]
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

  @Cached(ModificationTracker.NEVER_CHANGED, null)
  private def isMetaParadiseJar(pathname: String): Boolean = new File(pathname) match {
    case file if containsEntry(file, "scalac-plugin.xml") =>
      def hasAttribute(nameSuffix: String, value: String) = getJarAttribute(
        file,
        new Attributes.Name(s"Specification-$nameSuffix")
      ) == value

      hasAttribute("Vendor", "org.scalameta") &&
        hasAttribute("Title", "paradise")
    case _ => false
  }

}

