package org.jetbrains.plugins.scala.lang.parser

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase

class ScalaFileSetParserTestCase(
  path: String,
  testFileExtensions: String*
) extends ScalaFileSetTestCase(path: String, testFileExtensions: _*) {

  def this(path: String) = this(path, Nil: _*)

  override protected def transform(testName: String, fileText: String, project: Project): String = {
    val lightFile = createLightFile(fileText, project)
    val psiDebugString = psiToString(lightFile, true)
    psiDebugString.replace(": " + lightFile.getName, "")
  }
}
