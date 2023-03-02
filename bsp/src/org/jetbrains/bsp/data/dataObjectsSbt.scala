package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.serialization.PropertyMapping
import org.jetbrains.bsp.data.BspEntityData.datakey
import org.jetbrains.sbt.project.data.MyURI

import java.util

/**
 * Data describing a "build" module: The IDEA-side representation of the sbt meta-project
 *
 * @param id          id of the build module
 * @param childrenIds ids associated with the modules that this build module describes the build for
 * @param imports     implicit sbt file imports.
 * @note for a SBT external build system entity see [[org.jetbrains.sbt.project.data.SbtBuildModuleData]]
 */
@SerialVersionUID(3)
case class SbtBuildModuleDataBsp @PropertyMapping(Array(
  "id",
  "childrenIds",
  "imports",
))(
  id: MyURI,
  childrenIds: util.List[MyURI],
  imports: util.List[String],
) extends BspEntityData

object SbtBuildModuleDataBsp {
  val Key: Key[SbtBuildModuleDataBsp] = datakey(classOf[SbtBuildModuleDataBsp])
}

/**
 * Data describing a project which is part of an sbt build.
 *
 * @note for a SBT external build system entity see [[org.jetbrains.sbt.project.data.SbtModuleData]]
 */
@SerialVersionUID(2)
case class SbtModuleDataBsp @PropertyMapping(Array(
  "id",
  "buildModuleId"
))(
  id: MyURI,
  buildModuleId: MyURI
) extends BspEntityData

object SbtModuleDataBsp {
  val Key: Key[SbtModuleDataBsp] = datakey(classOf[SbtModuleDataBsp])
}
