import com.intellij.util.io.HttpRequests
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.{DependencyManager, ScalaVersion}
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.net.URLClassLoader
import scala.collection.mutable.ListBuffer

def getCompilerPackageUrl(isDotty: Boolean): String = {
  val version = if (isDotty) "scala3-compiler_3" else "scala-compiler"

  s"https://package-search.services.jetbrains.com/api/package/org.scala-lang:$version"
}

def getCompilerPackageInfo(isDotty: Boolean): JsObject =
  HttpRequests
    .request(getCompilerPackageUrl(isDotty))
    .accept("application/vnd.jetbrains.packagesearch.minimal.v2+json")
    .readString()
    .parseJson
    .asJsObject

def isVersionStable(version: String): Boolean = {
  val unstablePattern = """.*[a-zA-Z-].*"""
  !version.matches(unstablePattern)
}

/** Get stable compiler versions via the Package Search API
 *
 * @param isDotty
 * if true - load Scala 3 versions, otherwise Scala 2
 * */
def stableVersions(isDotty: Boolean): List[String] = getCompilerPackageInfo(isDotty)
  .fields.get("package")
  .toList
  .flatMap { packageObject =>
    val versionsArray = packageObject.asJsObject.fields.get("versions")

    versionsArray match {
      case Some(JsArray(elements)) =>
        elements.flatMap(_.asJsObject.fields.get("version")).collect {
          case JsString(version) => version
        }
      case _ => Seq.empty
    }
  }
  .filter(isVersionStable)

/** Get all Scala 2 and Scala 3 stable versions, keep only latest version for each lang level and sort ascending */
val latestVersions = (stableVersions(isDotty = false) ++ stableVersions(isDotty = true))
  .flatMap(ScalaVersion.fromString)
  .groupBy(_.languageLevel)
  .view
  .mapValues(_.maxBy(_.minorVersion))
  .values
  .toList
  .sortBy(_.languageLevel)

/** Add artifact descriptions (compiler, library and reflect if needed) for each lang level */
val artifactsWithLanguageLevel = latestVersions
  .map { version =>
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

def loadClass(name: String)(implicit classLoader: ClassLoader): Class[_] =
  Class.forName(name, true, classLoader)

def iteratorToList(iterator: Any)(implicit classLoader: ClassLoader): List[Any] = {
  val iteratorClass = loadClass("scala.collection.Iterator")
  val hasNextMethod = iteratorClass.getMethod("hasNext")
  val nextMethod = iteratorClass.getMethod("next")

  val builder = ListBuffer.empty[Any]
  while (hasNextMethod.invoke(iterator).asInstanceOf[Boolean]) {
    builder += nextMethod.invoke(iterator)
  }

  builder.result()
}

def iterableLikeToList(langLevel: ScalaLanguageLevel, iterable: Any)(implicit classLoader: ClassLoader): List[Any] = {
  val iterableLikeClassName =
    if (langLevel >= ScalaLanguageLevel.Scala_2_13) "scala.collection.IterableOnce"
    else "scala.collection.IterableLike"

  val iterableLikeClass = loadClass(iterableLikeClassName)
  val iteratorMethod = iterableLikeClass.getMethod("iterator")

  iteratorToList(iteratorMethod.invoke(iterable))
}

def getSecondElementOfTuple2(pair: Any)(implicit classLoader: ClassLoader): Any =
  loadClass("scala.Tuple2")(classLoader)
    .getDeclaredField("_2")
    .get(pair)

/** Convert compiler settings to the SbtScalacOptionInfo class via reflection
 *
 * @param list
 * Scalac Settings from the compiler (loaded via classLoader)
 * @param additionalMapping
 * some compiler versions define settings as an iterable of Setting, others use HashMap.
 * so there might be needed an additional mapping to get a List[Setting]
 * */
def convertSettings(list: List[Any], langLevel: ScalaLanguageLevel, additionalMapping: Option[Any => Any])
                   (implicit classLoader: ClassLoader): List[SbtScalacOptionInfo] = {
  val isDotty = langLevel >= ScalaLanguageLevel.Scala_3_0

  val settingClassName =
    if (isDotty) "dotty.tools.dotc.config.Settings$Setting"
    else "scala.tools.nsc.settings.MutableSettings$Setting"
  val settingClass = loadClass(settingClassName)

  val nameField = settingClass.getDeclaredField("name")
  nameField.setAccessible(true)

  val descriptionFieldName = if (isDotty) "description" else "helpDescription"
  val descriptionField = settingClass.getDeclaredField(descriptionFieldName)
  descriptionField.setAccessible(true)

  val isInternalOnly: Any => Boolean =
    if (isDotty) _ => false
    else {
      val isInternalOnlyMethod = settingClass.getMethod("isInternalOnly")
      isInternalOnlyMethod.invoke(_).asInstanceOf[Boolean]
    }

  val settings = additionalMapping.fold(list)(list.map)

  settings.collect {
    case setting if !isInternalOnly(setting) =>
      SbtScalacOptionInfo(
        flag = nameField.get(setting).asInstanceOf[String],
        description = descriptionField.get(setting).asInstanceOf[String],
        scalaVersions = Set(langLevel)
      )
  }
}

/** Get all compiler settings for the given lang level via reflection.
 *
 * @param classLoader
 * the class loader with artifacts for the given lang level
 * */
def getScalacOptions(langLevel: ScalaLanguageLevel)(implicit classLoader: ClassLoader): List[SbtScalacOptionInfo] = {
  val isDotty = langLevel >= ScalaLanguageLevel.Scala_3_0
  val additionalMapping =
    Option.when(!isDotty && langLevel > ScalaLanguageLevel.Scala_2_11)(getSecondElementOfTuple2(_))

  val settingsClassName =
    if (isDotty) "dotty.tools.dotc.config.ScalaSettings"
    else "scala.tools.nsc.doc.Settings"
  val settingsClass = loadClass(settingsClassName)

  settingsClass.getDeclaredConstructors
    .sortBy(_.getParameterCount)
    .headOption
    .map { constructor =>
      val settingsInstance = constructor.newInstance(Seq.fill(constructor.getParameterCount)(null): _*)

      val allSettingsMethod = settingsClass.getMethod("allSettings")
      val allSettings = allSettingsMethod.invoke(settingsInstance)

      convertSettings(iterableLikeToList(langLevel, allSettings), langLevel, additionalMapping)
    }
    .getOrElse(Nil)
}

def createClassLoaderWithArtifacts(scalaArtifacts: Seq[DependencyDescription]) = {
  val compilerClasspath = DependencyManager.resolve(scalaArtifacts: _*)
    .map(_.file.toURI.toURL)
    .toArray

  new URLClassLoader(compilerClasspath)
}

def mergeScalacOptions(left: SbtScalacOptionInfo, right: SbtScalacOptionInfo): SbtScalacOptionInfo =
  right.copy(scalaVersions = left.scalaVersions | right.scalaVersions)

// Load options for all given versions
val options = artifactsWithLanguageLevel
  .flatMap { case (scalaArtifacts, languageLevel) =>
    implicit val classLoader: ClassLoader = createClassLoaderWithArtifacts(scalaArtifacts)

    getScalacOptions(languageLevel)
  }
  .groupMapReduce(_.flag)(identity)(mergeScalacOptions)
  .values
  .toList
  .sortBy(_.flag)

// Paste the output to the "scalac-options.json" file
println(options.toJson.prettyPrint)
