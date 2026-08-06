package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.data.BspEntityData.datakey
import org.jetbrains.sbt.project.data.{MyURI, PathData}

import java.nio.file.Path
import java.util
import java.util.Objects

/**
 * Data describing a "build" module: The IDEA-side representation of the sbt meta-project
 *
 * @param id          id of the build module
 * @param childrenIds ids associated with the modules that this build module describes the build for
 * @param imports     implicit sbt file imports.
 * @param sbtVersion  indicates a version of SBT in case of SBT us used over BSP
 * @note for a SBT external build system entity see [[org.jetbrains.sbt.project.data.SbtBuildModuleData]]
 */
case class SbtBuildModuleDataBsp @PropertyMapping(Array(
  "id",
  "childrenIds",
  "imports",
  "sbtVersion",
))(
  id: MyURI,
  childrenIds: util.List[MyURI],
  imports: util.List[String],
  @NotNull sbtVersion: String
) extends BspEntityData

object SbtBuildModuleDataBsp {
  val Key: Key[SbtBuildModuleDataBsp] = datakey(classOf[SbtBuildModuleDataBsp])
}

/**
 * Data describing a project which is part of an sbt build.
 *
 * @see [[BspProjectData]] for an explanation on the serialization compatibility.
 * @note for a similar SBT external build system entity see [[org.jetbrains.sbt.project.data.SbtModuleData]]
 * @note read the difference between `buildModuleId` and `baseDirectory` in [[org.jetbrains.sbt.project.data.SbtModuleData]]
 */
// The `java.io.File` `baseDirectory` field and the File-based factory/accessor APIs below are intentionally
// retained to preserve External System serialization compatibility (see the serialization note above).
//noinspection SSBasedInspection
class SbtModuleDataBsp private (
  val id: MyURI,
  val buildModuleId: MyURI,
  @Nullable val baseDirectory: java.io.File,
  @Nullable val _baseDirectoryPath: PathData
) extends AbstractExternalEntityData(BSP.ProjectSystemId) with Equals {

  /**
   * Only invoked by the External System serialization mechanism. It doesn't matter what values we provide here, they
   * are replaced by data from disk.
   */
  private def this() = this(null, null, null, null)

  //noinspection InstanceOf
  override def canEqual(that: Any): Boolean = that.isInstanceOf[SbtModuleDataBsp]

  override def equals(obj: Any): Boolean = obj match {
    case that: SbtModuleDataBsp =>
      that.canEqual(this) &&
        this.id == that.id &&
        this.buildModuleId == that.buildModuleId &&
        this.baseDirectoryValue == that.baseDirectoryValue
    case _ => false
  }

  override def hashCode(): Int = Objects.hash(id, buildModuleId, baseDirectoryValue)

  private def baseDirectoryValue: Option[PathData] =
    Option(_baseDirectoryPath).orElse(Option(baseDirectory).map(f => PathData(f.toPath)))
}

//noinspection SSBasedInspection
object SbtModuleDataBsp {
  val Key: Key[SbtModuleDataBsp] = datakey(classOf[SbtModuleDataBsp])

  def apply(id: MyURI, buildModuleId: MyURI, baseDirectory: Option[Path]): SbtModuleDataBsp =
    new SbtModuleDataBsp(
      id = id,
      buildModuleId = buildModuleId,
      baseDirectory = baseDirectory.map(_.toFile).orNull,
      _baseDirectoryPath = baseDirectory.map(PathData.apply).orNull
    )

  def unapply(data: SbtModuleDataBsp): Some[(MyURI, MyURI, Option[PathData])] =
    Some((data.id, data.buildModuleId, data.baseDirectoryValue))
}
