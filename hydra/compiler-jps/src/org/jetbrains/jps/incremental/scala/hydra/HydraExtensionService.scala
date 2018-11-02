package org.jetbrains.jps.incremental.scala.hydra

import org.jetbrains.jps.model.JpsProject
import com.intellij.openapi.diagnostic.{Logger => JpsLogger}

trait HydraExtensionService {
  protected def Log: JpsLogger

  def isEnabled(project: JpsProject): Boolean = {
    Log.info(s"Called ${this.getClass.getName} extension to check if it's enabled.")
    project != null &&  HydraSettingsManager.getHydraSettings(project).isHydraEnabled
  }
}
