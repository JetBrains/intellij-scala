package org.jetbrains.plugins.scala

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
  * @author mucianm 
  * @since 07.04.16.
  */
trait TestFixtureProvider {
  def getFixture: CodeInsightTestFixture

  implicit final def project: Project = getFixture.getProject

  implicit final def module: Module = getFixture.getModule
}
