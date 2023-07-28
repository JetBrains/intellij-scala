package org.jetbrains.plugins.scala.project.maven

import com.intellij.pom.java.LanguageLevel

class ScalaMavenImporterTest_JDK_11 extends ScalaMavenImporterTest {
  override protected def projectJdkVersion: Option[LanguageLevel] = Some(LanguageLevel.JDK_11)
}