package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk

/**
  * @author Pavel Fatin
  */
abstract class SdkResolver {
  def sdkOf(reference: SdkReference): Option[Sdk]
}

object SdkResolver {
  val EP_NAME: ExtensionPointName[SdkResolver] =
    ExtensionPointName.create("org.intellij.scala.sdkResolver")
}
