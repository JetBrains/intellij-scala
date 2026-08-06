package org.jetbrains.sbt.editor.documentationProvider

import org.jetbrains.sbt.MockSbt_1_0

class SbtDocumentationProviderTest_Sbt_1 extends SbtDocumentationProviderCommonTests with MockSbt_1_0 {
  
  def testBuildSyntax(): Unit = doGenerateSbtDocDescriptionTest(
    s"""val ${CARET}someKey = settingKey[Map[String, File]]("$commonDescription")""",
    commonDescription
  )

  def testBuildSyntaxWithRank(): Unit = doGenerateSbtDocDescriptionTest(
    s"""val ${CARET}someKey = settingKey[Map[String, File]]("$commonDescription").withRank(DSetting)""",
    commonDescription
  )
}