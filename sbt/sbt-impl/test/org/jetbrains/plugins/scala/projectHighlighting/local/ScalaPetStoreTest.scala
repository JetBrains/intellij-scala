package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase

class ScalaPetStoreTest extends SbtProjectHighlightingLocalProjectsTestBase {

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  override def projectName = "scala-pet-store"
}
