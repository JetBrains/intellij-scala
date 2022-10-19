package org.jetbrains.plugins.scala.lang.parser

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.{ScalaFeatures, ScalaLanguageLevel}

object ScalaParserTestCase extends ScalaFileSetTestCase("/parser/data") {
  private val version  = new ScalaVersion(ScalaLanguageLevel.Scala_2_13, "8")
  private val features = ScalaFeatures.forParserTests(version)

  override protected def transform(testName: String, fileText: String, project: Project): String = {
    val file = ScalaPsiElementFactory.createScalaFileFromText(
      fileText,
      features,
      shouldTrimText = false
    )(project)

    psiToString(file, true).replace(": " + file.getName, "")
  }
}
