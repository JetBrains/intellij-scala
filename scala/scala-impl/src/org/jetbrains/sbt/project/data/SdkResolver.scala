package org.jetbrains.sbt.project.data

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk

/**
  * @author Pavel Fatin
  */
abstract class SdkResolver {
  def sdkOf(reference: SdkReference): Option[Sdk]
}

object SdkResolver {
  var EP_NAME: ExtensionPointName[SdkResolver] =
    ExtensionPointName.create("org.intellij.scala.sdkResolver")
}
