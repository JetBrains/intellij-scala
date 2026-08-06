package org.jetbrains.sbt.editor.documentationProvider

import org.jetbrains.sbt.MockSbt_2

class SbtDocumentationProviderTest_Sbt_2 extends SbtDocumentationProviderCommonTests with MockSbt_2 {
  
  def testBuildSyntax(): Unit = doGenerateSbtDocDescriptionTest(
    s"""val ${CARET}someKey = settingKey[Map[String, File]]("$commonDescription")""",
    commonDescription
  )

  def testBuildSyntaxWithRank(): Unit = doGenerateSbtDocDescriptionTest(
    s"""val ${CARET}someKey = settingKey[Map[String, File]]("$commonDescription").withRank(DSetting)""",
    commonDescription
  )
}