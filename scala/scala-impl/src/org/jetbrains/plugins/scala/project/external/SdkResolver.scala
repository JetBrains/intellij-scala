package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

abstract class SdkResolver {
  def findSdk(reference: SdkReference): Option[Sdk]
}

object SdkResolver extends ExtensionPointDeclaration[SdkResolver](
  "org.intellij.scala.sdkResolver"
)