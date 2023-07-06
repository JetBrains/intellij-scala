package org.jetbrains.sbt.lang.completion

import com.intellij.util.io.HttpRequests
import junit.framework.TestCase
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{DependencyManager, ScalaVersion}
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.lang.reflect.Field
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.collection.mutable.ListBuffer

object UpdateScalacOptionsInfo {
  /**
   * Fetches latest versions of the compiler for each lang level starting from 2.11 and updates
   * scala/scala-impl/resources/org/jetbrains/sbt/language/completion/scalac-options.json
   */
  final class ScriptTestCase extends TestCase {
    def test(): Unit = {
      val latestVersions = getLatestCompilerVersionsSorted
      val artifactsWithLanguageLevel = latestVersions.map(getArtifactsWithLangLevels)

      val options = artifactsWithLanguageLevel
        .flatMap { case (scalaArtifacts, languageLevel) =>
          getScalacOptions(createClassLoaderWithArtifacts(scalaArtifacts), languageLevel)
        }
        // drop options that don't start with `-` if any
        .filter(_.flag.startsWith("-"))
        // grouping by arg type because some of the options have different argument types for different compiler versions
        // e.g.: -Ywarn-unused
        //    2.11        - BooleanSetting
        //    2.12, 2.13  - MultiChoiceSetting
        // grouping by default has the same reasoning: some of the options have different default values
        .groupMapReduce(o => (o.flag, o.argType, o.defaultValue))(identity)(mergeScalacOptions)
        .values
        .toList
        .sortBy(_.flag)

      updateJsonFile(options.toJson.prettyPrint)
    }
  }

  /** Get all Scala 2 and Scala 3 stable versions, keep only latest version for each lang level and sort ascending.
   * Discard versions below 2.11 */
  private def getLatestCompilerVersionsSorted: List[ScalaVersion] =
    getAllCompilerVersions
      .flatMap(ScalaVersion.fromString)
      .groupBy(_.languageLevel)
      .view
      .mapValues(_.maxBy(_.minorVersion))
      .values
      .toList
      .filter(_.languageLevel >= ScalaLanguageLevel.Scala_2_11)
      .sortBy(_.languageLevel)

  /** Add artifact descriptions (compiler, library and reflect if needed) for given version */
  private def getArtifactsWithLangLevels(version: ScalaVersion): (List[DependencyDescription], ScalaLanguageLevel) = {
    val langLevel = version.languageLevel

    val compilerArtifact = DependencyDescription.scalaArtifact("compiler", version)
    val libraryArtifact = DependencyDescription.scalaArtifact("library", version)
    val maybeReflectArtifact = Option.when(langLevel < ScalaLanguageLevel.Scala_3_0 &&
      langLevel >= ScalaLanguageLevel.Scala_2_10)(DependencyDescription.scalaArtifact("reflect", version))

    val artifactsList = List(compilerArtifact, libraryArtifact) ++ maybeReflectArtifact

    val artifacts = if (langLevel >= ScalaLanguageLevel.Scala_3_0)
      artifactsList.map(_.transitive())
    else artifactsList

    (artifacts, langLevel)
  }

  private def updateJsonFile(content: String): Unit = {
    val scalacOptionsJsonPath = Path.of(TestUtils.getTestDataPath)
      .getParent
      .resolve("resources/org/jetbrains/sbt/language/completion/scalac-options.json")

    Files.writeString(scalacOptionsJsonPath, content, StandardCharsets.UTF_8,
      StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  private val versionPattern = ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r

  private val compilerUrls = Seq("scala-compiler", "scala3-compiler_3").map { artifactId =>
    s"https://repo1.maven.org/maven2/org/scala-lang/$artifactId"
  }

  private def getAllCompilerVersions = compilerUrls.flatMap(getCompilerVersions)

  private def getCompilerVersions(url: String) =
    HttpRequests
      .request(url)
      .readString()
      .split("\n")
      .toList
      .collect {
        case versionPattern(version) => version
      }

  private def loadClass(name: String)(implicit classLoader: ClassLoader): Class[_] =
    Class.forName(name, true, classLoader)

  private def accessible(field: Field): Field = {
    field.setAccessible(true)
    field
  }

  private def declaredFieldByName(cls: Class[_], name: String): Field =
    accessible(cls.getDeclaredField(name))

  private def declaredFieldByType(cls: Class[_], tpe: Class[_]): Field = {
    val fields = cls.getDeclaredFields
    accessible(fields.find(field => tpe.isAssignableFrom(field.getType)).get)
  }

  private def iteratorToList(iterator: Any)(implicit classLoader: ClassLoader): List[Any] = {
    val iteratorClass = loadClass("scala.collection.Iterator")
    val hasNextMethod = iteratorClass.getMethod("hasNext")
    val nextMethod = iteratorClass.getMethod("next")

    val builder = ListBuffer.empty[Any]
    while (hasNextMethod.invoke(iterator).asInstanceOf[Boolean]) {
      builder += nextMethod.invoke(iterator)
    }

    builder.result()
  }

  private def iterableLikeToList(iterable: Any)
                                (implicit classLoader: ClassLoader, langLevel: ScalaLanguageLevel): List[Any] = {
    val iterableLikeClassName =
      if (langLevel >= ScalaLanguageLevel.Scala_2_13) "scala.collection.IterableOnce"
      else "scala.collection.IterableLike"

    val iterableLikeClass = loadClass(iterableLikeClassName)
    val iteratorMethod = iterableLikeClass.getMethod("iterator")

    iteratorToList(iteratorMethod.invoke(iterable))
  }

  private def toOption(opt: Any)(implicit classLoader: ClassLoader): Option[Any] = {
    val optionClass = loadClass("scala.Option")
    val isEmptyMethod = optionClass.getMethod("isEmpty")
    val getMethod = optionClass.getMethod("get")

    Option.unless(isEmptyMethod.invoke(opt).asInstanceOf[Boolean])(getMethod.invoke(opt))
  }

  private def getSecondElementOfTuple2(pair: Any)(implicit classLoader: ClassLoader): Any =
    loadClass("scala.Tuple2")(classLoader)
      .getDeclaredField("_2")
      .get(pair)

  private def createScalacOptionWithAliases(name: String, argType: ArgType, description: String,
                                            aliases: List[String], choices: List[String], default: Option[String])
                                           (implicit langLevel: ScalaLanguageLevel): List[SbtScalacOptionInfo] = {
    val scalaVersions = Set(langLevel)

    def scalacOption(flag: String) = SbtScalacOptionInfo(
      flag = flag,
      descriptions = scalaVersions.map(_ -> description).toMap,
      choices = if (choices.nonEmpty) Map(langLevel -> choices.toSet) else Map.empty,
      argType = argType,
      scalaVersions = scalaVersions,
      defaultValue = default
    )

    scalacOption(name) :: aliases.map(scalacOption)
  }


  private def convertScala3Settings(settings: List[Any])
                                   (implicit classLoader: ClassLoader, langLevel: ScalaLanguageLevel): List[SbtScalacOptionInfo] = {
    val settingsClass = loadClass("dotty.tools.dotc.config.Settings")

    // Class tags used to distinguish between different options when setting values
    // see: dotty.tools.dotc.config.Settings.Setting#tryToSet
    def tag(name: String) = settingsClass.getMethod(name).invoke(settingsClass)

    val BooleanTag = tag("BooleanTag")
    val IntTag = tag("IntTag")
    val StringTag = tag("StringTag")
    val ListTag = tag("ListTag")
    val VersionTag = tag("VersionTag")
    val OptionTag = tag("OptionTag")
    val OutputTag = tag("OutputTag")

    val settingClass = loadClass("dotty.tools.dotc.config.Settings$Setting")
    val classTagClass = loadClass("scala.reflect.ClassTag")

    val nameField = declaredFieldByName(settingClass, "name")
    val descriptionField = declaredFieldByName(settingClass, "description")
    val defaultField = declaredFieldByName(settingClass, "default")
    val prefixField = declaredFieldByName(settingClass, "prefix")
    val choicesField = declaredFieldByName(settingClass, "choices")
    val aliasesField = declaredFieldByName(settingClass, "aliases")
    val classTagField = declaredFieldByType(settingClass, classTagClass)

    val scalaVersionClass = loadClass("dotty.tools.dotc.config.ScalaVersion")
    val scalaVersionUnparseMethod = scalaVersionClass.getMethod("unparse")

    val maybeChoiceWithHelpClass = try Option(loadClass("dotty.tools.dotc.config.Settings$Setting$ChoiceWithHelp")) catch {
      case _: ClassNotFoundException => None
    }

    def scalaVersionToString(version: Any): String = scalaVersionUnparseMethod
      .invoke(version)
      .asInstanceOf[String]
      .stripSuffix(".")

    // TODO: `description: String` field of the ChoiceWithHelp class might be useful
    def getChoiceString(choice: Any): String =
      maybeChoiceWithHelpClass match {
        case Some(cls) if cls isInstance choice =>
          val choiceName = declaredFieldByName(cls, "name").get(choice)
          choiceName.toString
        case _ => choice.toString
      }

    def defaultScalaVersionStr(setting: Any): String = scalaVersionToString(defaultField.get(setting))

    def nonEmptyDefault(setting: Any): Option[String] =
      Option(defaultField.get(setting))
        .map(_.toString)
        .filter(_.nonEmpty)

    settings.flatMap { setting =>
      val name = nameField.get(setting).asInstanceOf[String]
      val description = descriptionField.get(setting).asInstanceOf[String]
      val choices = toOption(choicesField.get(setting)).fold(List.empty[Any])(iterableLikeToList(_))
      val aliases = iterableLikeToList(aliasesField.get(setting)).asInstanceOf[List[String]]

      val tag = classTagField.get(setting)

      val (defaultValue, argType) = tag match {
        case BooleanTag | OptionTag =>
          (None, ArgType.No)
        case ListTag =>
          (None, ArgType.Multiple)
        case VersionTag =>
          (Some(defaultScalaVersionStr(setting)), ArgType.OneAfterColon)
        case StringTag =>
          val prefix = prefixField.get(setting).asInstanceOf[String]
          val argType =
            if (prefix.nonEmpty) ArgType.OneAfterPrefix(prefix)
            else if (choices.nonEmpty) ArgType.OneAfterColon
            else ArgType.OneSeparate
          (nonEmptyDefault(setting), argType)
        case IntTag | OutputTag =>
          (nonEmptyDefault(setting), ArgType.OneSeparate)
      }

      val choicesAsStr =
        if (tag == VersionTag) choices.map(scalaVersionToString)
        else choices.map(getChoiceString)

      createScalacOptionWithAliases(name = name, argType = argType, description = description,
        aliases = aliases, choices = choicesAsStr, default = defaultValue)
    }
  }

  private def convertScala2Settings(settings: List[Any])
                                   (implicit classLoader: ClassLoader, langLevel: ScalaLanguageLevel): List[SbtScalacOptionInfo] = {
    val settingClass = loadClass("scala.tools.nsc.settings.MutableSettings$Setting")

    val nameField = declaredFieldByName(settingClass, "name")
    val descriptionField = declaredFieldByName(settingClass, "helpDescription")
    val valueMethod = settingClass.getMethod("value")
    val choicesMethod = settingClass.getMethod("choices")
    val aliasesMethod = settingClass.getMethod("abbreviations")

    val isInternalOnlyMethod = settingClass.getMethod("isInternalOnly")

    val intSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$IntSetting")
    val booleanSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$BooleanSetting")
    val prefixSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$PrefixSetting")
    val stringSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$StringSetting")
    val multiStringSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$MultiStringSetting")
    val choiceSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$ChoiceSetting")
    val phasesSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$PhasesSetting")
    val scalaVersionSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$ScalaVersionSetting")
    val multiChoiceSettingClass = loadClass("scala.tools.nsc.settings.MutableSettings$MultiChoiceSetting")

    val prefixField = declaredFieldByName(prefixSettingClass, "prefix")

    val scalaVersionClass = loadClass("scala.tools.nsc.settings.ScalaVersion")
    val scalaVersionUnparseMethod = scalaVersionClass.getMethod("unparse")

    def defaultScalaVersionStr(setting: Any): String = scalaVersionUnparseMethod
      .invoke(valueMethod.invoke(setting))
      .asInstanceOf[String]
      .stripSuffix(".")

    def nonEmptyDefault(setting: Any): Option[String] =
      Option(valueMethod.invoke(setting))
        .map(_.toString)
        .filter(_.nonEmpty)

    settings
      .filterNot(isInternalOnlyMethod.invoke(_).asInstanceOf[Boolean])
      .flatMap { setting =>
        val name = nameField.get(setting).asInstanceOf[String]
        val description = descriptionField.get(setting).asInstanceOf[String]
        val choices = iterableLikeToList(choicesMethod.invoke(setting)).asInstanceOf[List[String]]
        val aliases = iterableLikeToList(aliasesMethod.invoke(setting)).asInstanceOf[List[String]]

        val (defaultValue, argType) =
          if (booleanSettingClass isInstance setting)
            (None, ArgType.No)
          else if (prefixSettingClass isInstance setting)
            (None, ArgType.OneAfterPrefix(prefixField.get(setting).asInstanceOf[String]))
          else if (intSettingClass isInstance setting)
            (nonEmptyDefault(setting), ArgType.OneSeparate)
          else if (stringSettingClass isInstance setting)
            (nonEmptyDefault(setting), ArgType.OneSeparate)
          else if (scalaVersionSettingClass isInstance setting)
            (Some(defaultScalaVersionStr(setting)), ArgType.OneAfterColon)
          else if (choiceSettingClass isInstance setting)
            (nonEmptyDefault(setting), ArgType.OneAfterColon)
          else if (multiChoiceSettingClass isInstance setting)
            (None, ArgType.Multiple)
          else if (multiStringSettingClass isInstance setting)
            (None, ArgType.Multiple)
          else {
            assert(phasesSettingClass isInstance setting, s"Unknown setting type: ${setting.getClass}")
            (None, ArgType.Multiple)
          }

        createScalacOptionWithAliases(name = name, argType = argType, description = description,
          aliases = aliases, choices = choices, default = defaultValue)
      }
  }

  /** Convert compiler settings to the SbtScalacOptionInfo class via reflection
   *
   * @param list
   * Scalac Settings from the compiler (loaded via classLoader)
   * @param additionalMapping
   * some compiler versions define settings as an iterable of Setting, others use HashMap.
   * so there might be needed an additional mapping to get a List[Setting]
   * */
  private def convertSettings(list: List[Any], additionalMapping: Option[Any => Any])
                             (implicit classLoader: ClassLoader, langLevel: ScalaLanguageLevel): List[SbtScalacOptionInfo] = {
    val settings = additionalMapping.fold(list)(list.map)

    if (langLevel.isScala3) convertScala3Settings(settings)
    else convertScala2Settings(settings)
  }

  /** Get all compiler settings for the given lang level via reflection.
   *
   * @param classLoader
   * the class loader with artifacts for the given lang level
   * */
  private def getScalacOptions(implicit classLoader: ClassLoader, langLevel: ScalaLanguageLevel): List[SbtScalacOptionInfo] = {
    val additionalMapping =
      Option.when(langLevel.isScala2 && langLevel > ScalaLanguageLevel.Scala_2_11)(getSecondElementOfTuple2(_))

    val settingsClassName =
      if (langLevel.isScala3) "dotty.tools.dotc.config.ScalaSettings"
      else "scala.tools.nsc.doc.Settings"
    val settingsClass = loadClass(settingsClassName)

    settingsClass.getDeclaredConstructors
      .sortBy(_.getParameterCount)
      .headOption
      .map { constructor =>
        val settingsInstance = constructor.newInstance(Seq.fill(constructor.getParameterCount)(null): _*)

        val allSettingsMethod = settingsClass.getMethod("allSettings")
        val allSettings = allSettingsMethod.invoke(settingsInstance)

        convertSettings(iterableLikeToList(allSettings), additionalMapping)
      }
      .getOrElse(Nil)
  }

  private def createClassLoaderWithArtifacts(scalaArtifacts: Seq[DependencyDescription]): ClassLoader = {
    val compilerClasspath = DependencyManager.resolve(scalaArtifacts: _*)
      .map(_.file.toURI.toURL)
      .toArray

    new URLClassLoader(compilerClasspath, null, null)
  }

  private def mergeScalacOptions(left: SbtScalacOptionInfo, right: SbtScalacOptionInfo): SbtScalacOptionInfo = {
    assert(left.flag == right.flag, "Cannot merge scalac options with different flags:\n" +
      s"\tleft is '${left.flag}' (${left.scalaVersions}), right is ${right.flag} (${right.scalaVersions})")
    assert(left.argType == right.argType, "Cannot merge scalac options with different arg types:\n" +
      s"\tleft[${left.flag}] is ${left.argType} (${left.scalaVersions}), right[${right.flag}] is ${right.argType} (${right.scalaVersions})")
    assert(left.defaultValue == right.defaultValue, "Cannot merge scalac options with different default values:\n" +
      s"\tleft[${left.flag}] is ${left.defaultValue} (${left.scalaVersions}), right[${right.flag}] is ${right.defaultValue} (${right.scalaVersions})")

    assert(left.productArity == 3 + 3, "Make sure that all fields are processed during scalac options merge")

    val descriptions = left.descriptions ++ right.descriptions
    val choices = left.choices ++ right.choices
    val versions = left.scalaVersions | right.scalaVersions

    right.copy(descriptions = descriptions, choices = choices, scalaVersions = versions)
  }
}
