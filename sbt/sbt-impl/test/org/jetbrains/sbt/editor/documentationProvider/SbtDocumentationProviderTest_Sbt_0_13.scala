package org.jetbrains.sbt.editor.documentationProvider

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.MockSbt_0_13

class SbtDocumentationProviderTest_Sbt_0_13 extends SbtDocumentationProviderCommonTests with MockSbt_0_13 {
  override val sbtVersion: Version = Version("0.13.18")
}