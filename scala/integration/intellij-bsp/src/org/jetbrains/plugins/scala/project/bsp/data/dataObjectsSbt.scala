package org.jetbrains.plugins.scala.project.bsp.data

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.project.bsp.data.BspEntityData.datakey

import java.io.File
import java.util

/**
 * Data describing a "build" module: The IDEA-side representation of the sbt meta-project
 *
 * @param id          id of the build module
 * @param childrenIds ids associated with the modules that this build module describes the build for
 * @param imports     implicit sbt file imports.
 * @param sbtVersion  indicates a version of SBT in case of SBT us used over BSP
 * @note for a SBT external build system entity see [[org.jetbrains.sbt.project.data.SbtBuildModuleData]]
 */
@SerialVersionUID(4)
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
 * @note for a similar SBT external build system entity see [[org.jetbrains.sbt.project.data.SbtModuleData]]
 * @note read the difference between `buildModuleId` and `baseDirectory` in [[org.jetbrains.sbt.project.data.SbtModuleData]]
 */
@SerialVersionUID(2)
case class SbtModuleDataBsp @PropertyMapping(Array(
  "id",
  "buildModuleId",
  "baseDirectory",
))(
  id: MyURI,
  buildModuleId: MyURI,
  @Nullable baseDirectory: File,
) extends BspEntityData {
  //Default constructor is needed in order intellij can deserialize data in old format with some fields missing
  def this() = this(null, null, null)
}

object SbtModuleDataBsp {
  val Key: Key[SbtModuleDataBsp] = datakey(classOf[SbtModuleDataBsp])
}

