package org.jetbrains.plugins.scala.codeInspection.feature

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

abstract class LanguageFeatureInspectionTestBase extends ScalaInspectionTestBase {
  override final protected val classOfInspection = classOf[LanguageFeatureInspection]
}