package org.jetbrains.sbt.editor.documentationProvider

import org.jetbrains.sbt.{MockSbt_0_13, SbtVersion}

class SbtDocumentationProviderTest_Sbt_0_13 extends SbtDocumentationProviderCommonTests with MockSbt_0_13 {
  override val sbtVersion: SbtVersion = SbtVersion("0.13.18")
}