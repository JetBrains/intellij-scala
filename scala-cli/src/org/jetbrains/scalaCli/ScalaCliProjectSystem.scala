package org.jetbrains.scalaCli

import com.intellij.openapi.externalSystem.model.ProjectSystemId

object ScalaCliProjectSystem {
  val Id = new ProjectSystemId("ScalaCLI", ScalaCliBundle.message("scala.cli.project.system.readable.name"))
}