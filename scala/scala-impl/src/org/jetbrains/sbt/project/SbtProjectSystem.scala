package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.plugins.scala.NlsString

/**
 * @author Pavel Fatin
 */
object SbtProjectSystem {
  val Id = new ProjectSystemId("SBT", NlsString.force(Sbt.Name))
}