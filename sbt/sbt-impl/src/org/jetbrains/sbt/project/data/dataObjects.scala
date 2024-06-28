package org.jetbrains.sbt.project.data

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.sbt.RichSeq
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtEntityData._
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.{ParsedValue, SeqStringParsedValue, StringParsedValue}
import org.jetbrains.sbt.resolvers.SbtResolver

import java.io.{File, Serializable}
import java.net.URI
import java.util
import java.util.{Objects, HashMap => JHashMap, List => JList, Map => JMap, Set => JSet}
import scala.jdk.CollectionConverters._

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
                /* note: the weight was changed due to the `SbtNestedModuleData` and `SbtSourceSetData` creation.
                It may happen that, for a given `SbtEntityData`, it wants to find its parent dataNode module via a `AbstractModuleDataService.MODULE_KEY` key.
                The key `AbstractModuleDataService.MODULE_KEY` is assigned to special module types within their respective services
                (to be precise this is happening inside `AbstractModuleDataService`, but it is called from the services),
                so it is necessary for this assignment to be made before the rest of the other services.
                 */
                 weight: Int = ProjectKeys.MODULE.getProcessingWeight + 3
                ): Key[T] = new Key(clazz.getName, weight)
}

/**
  * Data describing a "build" module: The IDEA-side representation of the sbt meta-project
  * @param imports implicit sbt file imports.
  * @param resolvers resolvers for this build project
  * @param buildFor id of the project that this module describes the build for
  */
case class SbtBuildModuleData @PropertyMapping(Array("imports", "resolvers", "buildFor"))(
  imports: JList[String],
  resolvers: JSet[SbtResolver],
  buildFor: MyURI
) extends SbtEntityData

object SbtBuildModuleData {
  val Key: Key[SbtBuildModuleData] = datakey(classOf[SbtBuildModuleData])

  def apply(imports: Seq[String], resolvers: Set[SbtResolver], buildFor: URI): SbtBuildModuleData =
    new SbtBuildModuleData(imports.toJavaList, toJavaSet(resolvers), new MyURI(buildFor))

  def apply(imports: Seq[String], resolvers: Set[SbtResolver], buildFor: MyURI): SbtBuildModuleData =
    new SbtBuildModuleData(imports.toJavaList, toJavaSet(resolvers), buildFor)
}

/**
 * Data describing a project which is part of an sbt build
 *
 * @param buildURI      ~ `sbt.ProjectRef#build()`<br>
 *                      Note `buildURI` doesn't always equal to `baseDirectory`.
 *                      For example if you have this sub-project: {{{
 *                        lazy val uriSchemeGit = RootProject(uri("https://github.com/JetBrains/sbt-idea-plugin.git"))
 *                      }}}
 *                      then `baseURI` equals to `https://github.com/JetBrains/sbt-idea-plugin.git`<br>
 *                      and `baseDirectory` will be something like `~/.sbt/1.0/staging/_some_hash_/sbt-idea-plugin/`
 * @param baseDirectory ~ `sbt.Keys$#baseDirectory()`<br>
 *                      Ideally shouldn't be @Nullable (cause org.jetbrains.sbt.structure.ProjectData#base() is not nullable)
 *                      but we have to mark it @Nullable to avoid deserialization exceptions when transiting from IDEA 2023.2
 *                      It can be removed later if Platform ES STORAGE_VERSION is incremented in future releases
 *                      (see IDEA-314999 for the details)
 */
case class SbtModuleData @PropertyMapping(Array(
  "id",
  "buildURI",
  "baseDirectory"
))(
  id: String,
  buildURI: MyURI,
  @Nullable baseDirectory: File
) extends SbtEntityData {
  //Default constructor is needed in order intellij can deserialize data in old format with some fields missing
  def this() = this(null, null, null)
}

object SbtModuleData {
  val Key: Key[SbtModuleData] = datakey(classOf[SbtModuleData])

  def apply(id: String, buildURI: URI, baseDirectory: File): SbtModuleData =
    new SbtModuleData(id, new MyURI(buildURI), baseDirectory)
}

case class SbtProjectData @PropertyMapping(Array("jdk", /*"javacOptions",*/ "sbtVersion", "projectPath", "projectTransitiveDependenciesUsed"))(
  @Nullable jdk: SdkReference,
  //javacOptions: JList[String], // see the commit message, why we don't need javacOptions at the project level
  sbtVersion: String,
  projectPath: String,
  //TODO Ideally this property should be stored in the workspace model
  projectTransitiveDependenciesUsed: Boolean,
  prodTestSourcesSeparated: Boolean
) extends SbtEntityData {
  //Default constructor is needed in order intellij can deserialize data in old format with some fields missing
  def this() = this(null, "1.0.0", ".", false, false)
}

object SbtProjectData {
  val Key: Key[SbtProjectData] = datakey(classOf[SbtProjectData])

  def apply(
    jdk: Option[SdkReference],
    sbtVersion: String,
    projectPath: String,
    projectTransitiveDependenciesUsed: Boolean,
    prodTestSourcesSeparated: Boolean
  ): SbtProjectData =
    SbtProjectData(
      jdk.orNull,
      sbtVersion,
      projectPath,
      projectTransitiveDependenciesUsed,
      prodTestSourcesSeparated
    )
}

sealed trait SbtNamedKey {
  val name: String
}

sealed trait SbtRankedKey {
  val rank: Int
}

case class SbtSettingData @PropertyMapping(Array("name", "description", "rank", "value"))(
  override val name: String,
  @Nls description: String,
  override val rank: Int,
  value: String
) extends SbtEntityData with SbtNamedKey with SbtRankedKey

object SbtSettingData {
  val Key: Key[SbtSettingData] = datakey(classOf[SbtSettingData])
}

case class SbtTaskData @PropertyMapping(Array("name", "description", "rank")) (
  override val name: String,
  @Nls description: String,
  override val rank: Int
) extends SbtEntityData with SbtNamedKey with SbtRankedKey

object SbtTaskData {
  val Key: Key[SbtTaskData] = datakey(classOf[SbtTaskData])
}

case class SbtCommandData @PropertyMapping(Array("name", "help")) (
  override val name: String,
  help: JMap[String, String]
) extends SbtEntityData with SbtNamedKey

object SbtCommandData {
  val Key: Key[SbtCommandData] = datakey(classOf[SbtCommandData])

  def apply(name: String, help: Seq[(String, String)]): SbtCommandData =
    SbtCommandData(name, toJavaMap(help.toMap))
}

//TODO remove scalaVersion, scalacClasspath, scaladocExtraClasspath after the 2023.2 release
/**
 * @param scalacClasspath        contains jars required to create scala compiler instance
 * @param scaladocExtraClasspath contains extra jars required to run ScalaDoc in Scala 3<br>
 *                               Needs to be added to `scalacClasspath`<br>
 *                               For Scala 2 it is empty, because scaladoc generation is built into compiler
 */
case class SbtModuleExtData @PropertyMapping(Array("scalaVersion", "scalacClasspath", "scaladocExtraClasspath", "scalacOptions", "sdk", "javacOptions", "packagePrefix", "basePackage", "compileOrder")) (
  @Nullable scalaVersion: String,
  scalacClasspath: JList[File],
  scaladocExtraClasspath: JList[File],
  scalacOptions: JList[String],
  @Nullable sdk: SdkReference,
  javacOptions: JList[String],
  packagePrefix: String,
  basePackage: String,
  compileOrder: CompileOrder
) extends SbtEntityData

object SbtModuleExtData {
  val Key: Key[SbtModuleExtData] = datakey(classOf[SbtModuleExtData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)

  def apply(
    scalaVersion: Option[String],
    scalacClasspath: Seq[File] = Seq.empty,
    scaladocExtraClasspath: Seq[File] = Seq.empty,
    scalacOptions: Seq[String] = Seq.empty,
    sdk: Option[SdkReference] = None,
    javacOptions: Seq[String] = Seq.empty,
    packagePrefix: Option[String] = None,
    basePackage: Option[String] = None,
    compileOrder: CompileOrder = CompileOrder.Mixed
  ): SbtModuleExtData =
    new SbtModuleExtData(
      scalaVersion.orNull,
      scalacClasspath.toJavaList,
      scaladocExtraClasspath.toJavaList,
      scalacOptions.toJavaList,
      sdk.orNull,
      javacOptions.toJavaList,
      packagePrefix.orNull,
      basePackage.orNull,
      compileOrder
    )
}

/**
 * @param scalacClasspath        contains jars required to create scala compiler instance
 * @param scaladocExtraClasspath contains extra jars required to run ScalaDoc in Scala 3<br>
 *                               Needs to be added to `scalacClasspath`<br>
 *                               For Scala 2 it is empty, because scaladoc generation is built into compiler
 */
case class SbtScalaSdkData @PropertyMapping(Array(
  "scalaVersion",
  "scalacClasspath",
  "scaladocExtraClasspath",
  "compilerBridgeBinaryJar"
)) (
  @Nullable scalaVersion: String,
  scalacClasspath: JList[File],
  scaladocExtraClasspath: JList[File],
  @Nullable compilerBridgeBinaryJar: File
) extends SbtEntityData {
  //Default constructor is needed in order intellij can deserialize data in old format with some fields missing
  def this() = this(null, new util.ArrayList(), new util.ArrayList(), null)
}

object SbtScalaSdkData {
  val Key: Key[SbtScalaSdkData] = datakey(classOf[SbtScalaSdkData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)

  def apply(
    scalaVersion: Option[String],
    scalacClasspath: Seq[File] = Seq.empty,
    scaladocExtraClasspath: Seq[File] = Seq.empty,
    @Nullable compilerBridgeBinaryJar: Option[File] = None
  ): SbtScalaSdkData =
    new SbtScalaSdkData(
      scalaVersion.orNull,
      scalacClasspath.toJavaList,
      scaladocExtraClasspath.toJavaList,
      compilerBridgeBinaryJar.orNull
    )
}


case class SbtPlay2ProjectData @PropertyMapping(Array("stringValues", "seqStringsValues")) (
  stringValues: JMap[String, JMap[String, StringParsedValue]],
  seqStringsValues: JMap[String, JMap[String, SeqStringParsedValue]]
) extends SbtEntityData {

  def projectKeys: Map[String, Map[String, ParsedValue[_]]] =
    (stringValues.asScala.toMap ++ seqStringsValues.asScala.toMap).map {
      case (k, v) => (k, v.asScala.toMap)
    }
}

object SbtPlay2ProjectData {
  val Key: Key[SbtPlay2ProjectData] = datakey(classOf[SbtPlay2ProjectData], ProjectKeys.PROJECT.getProcessingWeight + 1)

  def apply(projectKeys: Map[String, Map[String, ParsedValue[_]]]): SbtPlay2ProjectData = {
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
    SbtPlay2ProjectData(stringValues, seqStringsValues)
  }
}

/**
 * This URI wrapper class is a workaround for a [[java.net.URI]] deserialization bug (see IDEA-221074)<br>
 *
 * URI class uses single field for serialization: `String string;`<br>
 * In order we can deserialize old-serialized structure in new version of plugin we use the same backing field name & type
 * `private val string: String` (it must be a field, otherwise deserialization won't work)<br>
 *
 * So we are basically pretending that we are URI during serialization / deserialization.
 *
 * @todo remove when IDEA-221074 is fixed
 */
final class MyURI @PropertyMapping(Array("string"))(
  private val string: String
) extends Serializable {
  assert(string != null)

  @transient val uri: URI = new URI(string)

  def this(uri: URI) = {
    this(uri.toString)
  }

  override def toString: String = Objects.toString(uri)

  override def hashCode(): Int = Objects.hashCode(uri)

  override def equals(obj: Any): Boolean = obj match {
    case other: MyURI => uri == other.uri
    case _ => false
  }
}