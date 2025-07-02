package org.jetbrains.plugins.scala.project.maven

import com.intellij.pom.java.LanguageLevel
import org.junit.Ignore

@Ignore
class ScalaMavenImporterTest_InternalJdk extends ScalaMavenImporterTest {
  override protected def projectJdkVersion: Option[LanguageLevel] = None
}
