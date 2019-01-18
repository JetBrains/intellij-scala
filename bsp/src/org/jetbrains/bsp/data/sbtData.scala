package org.jetbrains.bsp.data

import java.net.URI

import org.jetbrains.sbt.project.data.SbtEntityData
import org.jetbrains.sbt.resolvers.SbtResolver

object sbtData {

}

/**
  * Data describing a "build" module: The IDEA-side representation of the sbt meta-project
  * @param imports implicit sbt file imports.
  * @param resolvers resolvers for this build project
  * @param buildFor id of the project that this module describes the build for
  */
@SerialVersionUID(2)
case class SbtBuildModuleData(imports: Seq[String],
                              buildFor: Seq[BuildTarget]) extends SbtEntityData

case class SbtBuildData()