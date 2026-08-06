package org.jetbrains.sbt.project.data

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.{Nls, NotNull, Nullable}
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.project.ReplClasspath
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.sbt.RichSeq
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtEntityData.*
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.{ParsedValue, SeqStringParsedValue, StringParsedValue}
import org.jetbrains.sbt.resolvers.SbtResolver

import java.net.URI
import java.nio.file.Path
import java.util.{Objects, HashMap as JHashMap, List as JList, Map as JMap, Set as JSet}
import scala.jdk.CollectionConverters.*

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
 * @see `org.jetbrains.bsp.data.BspProjectData` for an explanation on the serialization compatibility.
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
// The `java.io.File` `baseDirectory` field and the File-based factory/accessor APIs are intentionally retained
// to preserve External System serialization compatibility (see the @see reference above).
//noinspection SSBasedInspection
class SbtModuleData private (
  val id: String,
  val buildURI: MyURI,
  @Nullable val baseDirectory: java.io.File,
  @Nullable val _baseDirectoryPath: PathData
) extends AbstractExternalEntityData(SbtProjectSystem.Id) with Equals:

  /**
   * Only invoked by the External System serialization mechanism. It doesn't matter what values we provide here, they
   * are replaced by data from disk.
   */
  private def this() = this(null, null, null, null)

  //noinspection InstanceOf
  override def canEqual(that: Any): Boolean = that.isInstanceOf[SbtModuleData]

  override def equals(obj: Any): Boolean = obj match
    case that: SbtModuleData =>
      that.canEqual(this) &&
        this.id == that.id &&
        this.buildURI == that.buildURI &&
        this.baseDirectoryValue == that.baseDirectoryValue
    case _ => false

  override def hashCode(): Int = Objects.hash(id, buildURI, baseDirectoryValue)

  private def baseDirectoryValue: Option[PathData] =
    Option(_baseDirectoryPath).orElse(Option(baseDirectory).map(f => PathData(f.toPath)))

end SbtModuleData

//noinspection SSBasedInspection
object SbtModuleData:
  val Key: Key[SbtModuleData] = datakey(classOf[SbtModuleData])

  def apply(id: String, buildURI: URI, baseDirectory: Path): SbtModuleData =
    new SbtModuleData(
      id = id,
      buildURI = new MyURI(buildURI),
      baseDirectory = baseDirectory.toFile,
      _baseDirectoryPath = PathData(baseDirectory)
    )

  def unapply(data: SbtModuleData): Some[(String, MyURI, Option[PathData])] =
    Some((data.id, data.buildURI, data.baseDirectoryValue))

end SbtModuleData

case class SbtProjectData @PropertyMapping(Array("jdk", /*"javacOptions",*/ "sbtVersion", "projectPath", "projectTransitiveDependenciesUsed"))(
  @Nullable jdk: SdkReference,
  //javacOptions: JList[String], // see the commit message, why we don't need javacOptions at the project level
  sbtVersion: String,
  projectPath: String,
  prodTestSourcesSeparated: Boolean,
  isPreview: Boolean
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
    prodTestSourcesSeparated: Boolean,
    isPreview: Boolean = false
  ): SbtProjectData =
    SbtProjectData(
      jdk.orNull,
      sbtVersion,
      projectPath,
      prodTestSourcesSeparated,
      isPreview
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

/**
 * @param name module name, without any group or root module prefix.
 *             In practice, it will be the name or id of the sbt project - depending on whether the name is unique within a single build.
 *             If separate modules for production and test are enabled, the main/test suffix will also be present in the name.
 */
case class DisplayModuleNameData @PropertyMapping(Array("name"))(
  name: String
) extends SbtEntityData

object DisplayModuleNameData {
  val Key: Key[DisplayModuleNameData] = datakey(classOf[DisplayModuleNameData])
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

case class SbtModuleExtData @PropertyMapping(Array("scalacOptions", "sdk", "javacOptions", "kotlincOptions", "packagePrefix", "basePackage", "compileOrder")) (
  scalacOptions: JList[String],
  @Nullable sdk: SdkReference,
  javacOptions: JList[String],
  kotlincOptions: JList[String],
  packagePrefix: String,
  basePackage: String,
  compileOrder: CompileOrder
) extends SbtEntityData

object SbtModuleExtData {
  val Key: Key[SbtModuleExtData] = datakey(classOf[SbtModuleExtData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)

  def apply(
    scalacOptions: Seq[String] = Seq.empty,
    sdk: Option[SdkReference] = None,
    javacOptions: Seq[String] = Seq.empty,
    kotlincOptions: Seq[String] = Seq.empty,
    packagePrefix: Option[String] = None,
    basePackage: Option[String] = None,
    compileOrder: CompileOrder = CompileOrder.Mixed
  ): SbtModuleExtData =
    new SbtModuleExtData(
      scalacOptions.toJavaList,
      sdk.orNull,
      javacOptions.toJavaList,
      kotlincOptions.toJavaList,
      packagePrefix.orNull,
      basePackage.orNull,
      compileOrder
    )
}

/**
 * @see `org.jetbrains.bsp.data.BspProjectData` for an explanation on the serialization compatibility.
 *
 * @param scalacClasspath        contains jars required to create scala compiler instance
 * @param scaladocExtraClasspath contains extra jars required to run ScalaDoc in Scala 3<br>
 *                               Needs to be added to `scalacClasspath`<br>
 *                               For Scala 2 it is empty, because scaladoc generation is built into compiler
 */
// The `java.io.File` classpath/jar fields and the File-based factory/accessor APIs are intentionally retained
// to preserve External System serialization compatibility (see the @see reference above).
//noinspection SSBasedInspection
class SbtScalaSdkData private (
  @Nullable val scalaVersion: String,
  @NotNull val scalacClasspath: java.util.List[java.io.File],
  @Nullable val _scalacClasspathPaths: java.util.List[PathData],
  @NotNull val scaladocExtraClasspath: java.util.List[java.io.File],
  @Nullable val _scaladocExtraClasspathPaths: java.util.List[PathData],
  @Nullable val compilerBridgeBinaryJar: java.io.File,
  @Nullable val _compilerBridgeBinaryJarPath: PathData,
  @Nullable val replClasspath: java.util.List[PathData]
) extends AbstractExternalEntityData(SbtProjectSystem.Id) with Equals:

  /**
   * Only invoked by the External System serialization mechanism. It doesn't matter what values we provide here, they
   * are replaced by data from disk.
   */
  def this() = this(
    scalaVersion = null,
    scalacClasspath = java.util.Collections.emptyList(),
    _scalacClasspathPaths = null,
    scaladocExtraClasspath = java.util.Collections.emptyList(),
    _scaladocExtraClasspathPaths = null,
    compilerBridgeBinaryJar = null,
    _compilerBridgeBinaryJarPath = null,
    replClasspath = null
  )

  //noinspection InstanceOf
  override def canEqual(that: Any): Boolean = that.isInstanceOf[SbtScalaSdkData]

  override def equals(obj: Any): Boolean = obj match
    case that: SbtScalaSdkData =>
      that.canEqual(this) &&
        this.scalaVersionValue == that.scalaVersionValue &&
        this.scalacClasspathValue == that.scalacClasspathValue &&
        this.scaladocExtraClasspathValue == that.scaladocExtraClasspathValue &&
        this.compilerBridgeBinaryJarValue == that.compilerBridgeBinaryJarValue &&
        this.replClasspathValue == that.replClasspathValue
    case _ => false

  override def hashCode(): Int =
    Objects.hash(scalaVersionValue, scalacClasspathValue, scaladocExtraClasspathValue, compilerBridgeBinaryJarValue, replClasspathValue)

  private def scalaVersionValue: Option[String] = Option(scalaVersion)

  private def scalacClasspathValue: Seq[PathData] =
    if _scalacClasspathPaths == null then
      return scalacClasspath.stream().map[PathData](f => PathData(f.toPath)).toList.asScala.toSeq
    _scalacClasspathPaths.asScala.toSeq

  private def scaladocExtraClasspathValue: Seq[PathData] =
    if _scaladocExtraClasspathPaths == null then
      return scaladocExtraClasspath.stream().map[PathData](f => PathData(f.toPath)).toList.asScala.toSeq
    _scaladocExtraClasspathPaths.asScala.toSeq

  private def compilerBridgeBinaryJarValue: Option[PathData] =
    Option(_compilerBridgeBinaryJarPath)
      .orElse(Option(compilerBridgeBinaryJar).map(f => PathData(f.toPath)))

  private def replClasspathValue: Seq[PathData] =
    Option(replClasspath).map(_.asScala.toSeq).toSeq.flatten

end SbtScalaSdkData

//noinspection SSBasedInspection
object SbtScalaSdkData:
  val Key: Key[SbtScalaSdkData] = datakey(classOf[SbtScalaSdkData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)

  def apply(
    scalaVersion: Option[String],
    scalacClasspath: Seq[Path] = Seq.empty,
    scaladocExtraClasspath: Seq[Path] = Seq.empty,
    compilerBridgeBinaryJar: Option[Path] = Option.empty,
    replClasspath: ReplClasspath = ReplClasspath.Bundled
  ): SbtScalaSdkData =
    new SbtScalaSdkData(
      scalaVersion = scalaVersion.orNull,
      scalacClasspath = scalacClasspath.map(_.toFile).asJava,
      _scalacClasspathPaths = scalacClasspath.map(PathData.apply).asJava,
      scaladocExtraClasspath = scaladocExtraClasspath.map(_.toFile).asJava,
      _scaladocExtraClasspathPaths = scaladocExtraClasspath.map(PathData.apply).asJava,
      compilerBridgeBinaryJar = compilerBridgeBinaryJar.map(_.toFile).orNull,
      _compilerBridgeBinaryJarPath = compilerBridgeBinaryJar.map(PathData.apply).orNull,
      replClasspath = replClasspath.asPaths.map(PathData.apply).asJava
    )

  def unapply(data: SbtScalaSdkData): Some[(Option[String], Seq[PathData], Seq[PathData], Option[PathData], Seq[PathData])] =
    Some((data.scalaVersionValue, data.scalacClasspathValue, data.scaladocExtraClasspathValue, data.compilerBridgeBinaryJarValue, data.replClasspathValue))

end SbtScalaSdkData

case class SbtPlay2ProjectData @PropertyMapping(Array("stringValues", "seqStringsValues")) (
  stringValues: JMap[String, JMap[String, StringParsedValue]],
  seqStringsValues: JMap[String, JMap[String, SeqStringParsedValue]]
) extends SbtEntityData {

  def projectKeys: Map[String, Map[String, ParsedValue[?]]] =
    (stringValues.asScala.toMap ++ seqStringsValues.asScala.toMap).map {
      case (k, v) => (k, v.asScala.toMap)
    }
}

object SbtPlay2ProjectData {
  val Key: Key[SbtPlay2ProjectData] = datakey(classOf[SbtPlay2ProjectData], ProjectKeys.PROJECT.getProcessingWeight + 1)

  def apply(projectKeys: Map[String, Map[String, ParsedValue[?]]]): SbtPlay2ProjectData = {
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
