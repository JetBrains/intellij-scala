package org.jetbrains.sbt.editor.documentationProvider

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.{MockSbt_1_0, Sbt}

class SbtDocumentationProviderTest_Sbt_1 extends SbtDocumentationProviderTestBase
  with SbtDocumentationProviderCommonTests
  with MockSbt_1_0 {

  override val sbtVersion: Version = Sbt.LatestVersion

  def testBuildSyntax(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = settingKey[Map[String, File]]("$description")""",
    description
  )

  def testBuildSyntaxWithRank(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = settingKey[Map[String, File]]("$description").withRank(DSetting)""",
    description
  )
}