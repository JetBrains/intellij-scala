package org.jetbrains.plugins.scala.project

import java.io.File
import java.util.jar.Attributes

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.{OrderEnumerator, libraries}
import com.intellij.openapi.util.io.JarUtil.{containsEntry, getJarAttribute}
import com.intellij.util.CommonProcessors.FindProcessor
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel._
import org.jetbrains.plugins.scala.project.ScalaModuleSettings.{Yimports, YnoPredefOrNoImports}
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.sbt.settings.SbtSettings

private class ScalaModuleSettings(module: Module, val scalaSdk: LibraryEx) {

  val scalaLanguageLevel: ScalaLanguageLevel = scalaSdk.properties.languageLevel

  val settingsForHighlighting: collection.Seq[ScalaCompilerSettings] =
    ScalaCompilerConfiguration.instanceIn(module.getProject).settingsForHighlighting(module)

  val compilerPlugins: Set[String] = settingsForHighlighting.flatMap(_.plugins).toSet

  val additionalCompilerOptions: Set[String] = settingsForHighlighting.flatMap(_.additionalCompilerOptions).toSet

  val isMetaEnabled: Boolean =
    compilerPlugins.exists(isMetaParadiseJar)

  val hasScala3: Boolean = scalaLanguageLevel >= Scala_3_0

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
    val `is scala 2.12.2` = scalaSdk.compilerVersion.map {
      Version(_)
    }.exists {
      _ >= Version("2.12.2")
    }
    val `is sbt 1.0` = sbtVersion.exists {
      _ >= Version("1.0")
    }
    `is scala 2.12.2` || `is sbt 1.0`
  }

  val literalTypesEnabled: Boolean = scalaLanguageLevel >= ScalaLanguageLevel.Scala_2_13 ||
    additionalCompilerOptions.contains("-Yliteral-types")

  val kindProjectorPlugin: Option[String] =
    compilerPlugins.find(_.contains("kind-projector"))

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

  val isPartialUnificationEnabled: Boolean =
    scalaLanguageLevel >= Scala_2_13 || additionalCompilerOptions.contains("-Ypartial-unification")

  val isCompilerStrictMode: Boolean =
    settingsForHighlighting.exists(_.strict)

  val customDefaultImports: Option[Seq[String]] =
    additionalCompilerOptions.collectFirst {
      case Yimports(imports) if scalaLanguageLevel >= Scala_2_13 => imports
      case YnoPredefOrNoImports(imports)                         => imports
    }

  private[this] def isMetaParadiseJar(pathname: String): Boolean = new File(pathname) match {
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

private object ScalaModuleSettings {

  def apply(module: Module): Option[ScalaModuleSettings] = {
    val processor: FindProcessor[libraries.Library] = _.isScalaSdk

    OrderEnumerator.orderEntries(module)
      .librariesOnly
      .forEachLibrary(processor)

    val scalaSdk = processor.getFoundValue.asInstanceOf[LibraryEx]

    Option(scalaSdk)
      .map(new ScalaModuleSettings(module, _))
  }

  private object Yimports {
    private val YimportsPrefix = "-Yimports:"

    def unapply(setting: String): Option[Seq[String]] =
      if (setting.startsWith(YimportsPrefix))
        Option(setting.substring(YimportsPrefix.length).split(",").map(_.trim))
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
}

