package org.jetbrains.sbt.project.data

import java.io.File
import java.net.URI
import java.util.{HashMap => JHashMap, List => JList, Map => JMap, Set => JSet}

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.sbt.RichSeq
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtEntityData._
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.{ParsedValue, SeqStringParsedValue, StringParsedValue}
import org.jetbrains.sbt.resolvers.SbtResolver

import scala.collection.JavaConverters.mapAsScalaMapConverter

abstract class SbtEntityData extends AbstractExternalEntityData(SbtProjectSystem.Id) with Product {

  // need to manually specify equals/hashCode here because it is not generated for case classes inheriting from
  // AbstractExternalEntityData
  override def equals(obj: scala.Any): Boolean = obj match {
    case data: SbtEntityData =>
      //noinspection CorrespondsUnsorted
      this.canEqual(data) &&
        (this.productIterator sameElements data.productIterator)
    case _ => false
  }

  override def hashCode(): Int = runtime.ScalaRunTime._hashCode(this)

}
object SbtEntityData {
  def datakey[T](clazz: Class[T],
                 weight: Int = ProjectKeys.MODULE.getProcessingWeight + 1
                ): Key[T] = new Key(clazz.getName, weight)
}

/**
  * Data describing a "build" module: The IDEA-side representation of the sbt meta-project
  * @param imports implicit sbt file imports.
  * @param resolvers resolvers for this build project
  * @param buildFor id of the project that this module describes the build for
  */
@SerialVersionUID(2)
case class SbtBuildModuleData @PropertyMapping(Array("imports", "resolvers", "buildFor")) (
  imports: JList[String],
  resolvers: JSet[SbtResolver],
  buildFor: URI
) extends SbtEntityData

object SbtBuildModuleData {
  val Key: Key[SbtBuildModuleData] = datakey(classOf[SbtBuildModuleData])

  def apply(imports: Seq[String], resolvers: Set[SbtResolver], buildFor: URI): SbtBuildModuleData =
    SbtBuildModuleData(imports.toJavaList, toJavaSet(resolvers), buildFor)
}


/** Data describing a project which is part of an sbt build. */
@SerialVersionUID(1)
case class SbtModuleData @PropertyMapping(Array("id", "buildURI")) (id: String, buildURI: URI) extends SbtEntityData

object SbtModuleData {
  val Key: Key[SbtModuleData] = datakey(classOf[SbtModuleData])
}

@SerialVersionUID(1)
case class SbtProjectData @PropertyMapping(Array("basePackages", "jdk", "javacOptions", "sbtVersion", "projectPath"))(
  basePackages: JList[String],
  @Nullable jdk: SdkReference,
  javacOptions: JList[String],
  sbtVersion: String,
  projectPath: String
) extends SbtEntityData

object SbtProjectData {
  val Key: Key[SbtProjectData] = datakey(classOf[SbtProjectData])

  def apply(basePackages: Seq[String],
            jdk: Option[SdkReference],
            javacOptions: Seq[String],
            sbtVersion: String,
            projectPath: String): SbtProjectData =
    SbtProjectData(
      basePackages.toJavaList,
      jdk.orNull,
      javacOptions.toJavaList,
      sbtVersion,
      projectPath)
}

sealed trait SbtNamedKey {
  val name: String
}

sealed trait SbtRankedKey {
  val rank: Int
}

@SerialVersionUID(1)
case class SbtSettingData @PropertyMapping(Array("name", "description", "rank", "value"))(override val name: String,
                                                                                          @Nls description: String,
                                                                                          override val rank: Int,
                                                                                          value: String
)  extends SbtEntityData with SbtNamedKey with SbtRankedKey

object SbtSettingData {
  val Key: Key[SbtSettingData] = datakey(classOf[SbtSettingData])
}

@SerialVersionUID(1)
case class SbtTaskData @PropertyMapping(Array("name", "description", "rank")) (override val name: String,
                                                                               @Nls description: String,
                                                                               override val rank: Int) extends SbtEntityData with SbtNamedKey with SbtRankedKey

object SbtTaskData {
  val Key: Key[SbtTaskData] = datakey(classOf[SbtTaskData])
}

@SerialVersionUID(1)
case class SbtCommandData @PropertyMapping(Array("name", "help")) (override val name: String,
                                                                   help: JMap[String, String]
) extends SbtEntityData with SbtNamedKey

object SbtCommandData {
  val Key: Key[SbtCommandData] = datakey(classOf[SbtCommandData])

  def apply(name: String, help: Seq[(String, String)]): SbtCommandData =
    SbtCommandData(name, toJavaMap(help.toMap))
}

@SerialVersionUID(1)
case class ModuleExtData @PropertyMapping(Array("scalaVersion", "scalacClasspath", "scalacOptions", "sdk", "javacOptions")) (
  @Nullable scalaVersion: String,
  scalacClasspath: JList[File],
  scalacOptions: JList[String],
  @Nullable sdk: SdkReference,
  javacOptions: JList[String]
) extends SbtEntityData

object ModuleExtData {
  val Key: Key[ModuleExtData] = datakey(classOf[ModuleExtData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)

  def apply(scalaVersion: Option[String],
            scalacClasspath: Seq[File] = Seq.empty,
            scalacOptions: Seq[String] = Seq.empty,
            sdk: Option[SdkReference] = None,
            javacOptions: Seq[String] = Seq.empty): ModuleExtData =
    ModuleExtData(
      scalaVersion.orNull,
      scalacClasspath.toJavaList,
      scalacOptions.toJavaList,
      sdk.orNull,
      javacOptions.toJavaList
    )
}


@SerialVersionUID(1)
case class Play2ProjectData @PropertyMapping(Array("stringValues", "seqStringsValues")) (
  stringValues: JMap[String, JMap[String, StringParsedValue]],
  seqStringsValues: JMap[String, JMap[String, SeqStringParsedValue]]
) extends SbtEntityData {

  def projectKeys: Map[String, Map[String, ParsedValue[_]]] =
    (stringValues.asScala.toMap ++ seqStringsValues.asScala.toMap).map {
      case (k, v) => (k, v.asScala.toMap)
    }
}


object Play2ProjectData {
  val Key: Key[Play2ProjectData] = datakey(classOf[Play2ProjectData], ProjectKeys.PROJECT.getProcessingWeight + 1)

  def apply(projectKeys: Map[String, Map[String, ParsedValue[_]]]): Play2ProjectData = {
    val stringValues = new JHashMap[String, JMap[String, StringParsedValue]]()
    val seqStringsValues = new JHashMap[String, JMap[String, SeqStringParsedValue]]()
    for {
      (key, value) <- projectKeys
      (innerKey, innerValue) <- value
    } {
      innerValue match {
        case str: StringParsedValue =>
          val innerMap = stringValues.computeIfAbsent(key, _ => new JHashMap[String, StringParsedValue]())
          innerMap.put(innerKey, str)
        case seqStr: SeqStringParsedValue =>
          val innerMap = seqStringsValues.computeIfAbsent(key, _ => new JHashMap[String, SeqStringParsedValue]())
          innerMap.put(innerKey, seqStr)
      }
    }
    Play2ProjectData(stringValues, seqStringsValues)
  }
}

@SerialVersionUID(1)
case class AndroidFacetData @PropertyMapping(Array("version", "manifest", "apk", "res", "assets", "gen", "libs", "isLibrary", "proguardConfig")) (
  version: String,
  manifest: File,
  apk: File,
  res: File,
  assets: File,
  gen: File,
  libs: File,
  isLibrary: Boolean,
  proguardConfig: JList[String]
) extends SbtEntityData

object AndroidFacetData {
  // TODO Change to "+ 1" when external system will enable the proper service separation.
  // The external system now invokes data services regardless of system ID.
  // Consequently, com.android.tools.idea.gradle.project.sync.setup.* services in the Android plugin remove _all_ Android facets.
  // As a workaround, we now rely on the additional "weight" to invoke the service after the Android / Gradle's one.
  // We expect the external system to update the architecture so that different services will be properly separated.
  val Key: Key[AndroidFacetData] = datakey(classOf[AndroidFacetData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight +100500)

  def apply(version: String, manifest: File, apk: File,
            res: File, assets: File, gen: File, libs: File,
            isLibrary: Boolean, proguardConfig: Seq[String]): AndroidFacetData =
    AndroidFacetData(version, manifest, apk, res, assets, gen, libs, isLibrary, proguardConfig.toJavaList)
}
