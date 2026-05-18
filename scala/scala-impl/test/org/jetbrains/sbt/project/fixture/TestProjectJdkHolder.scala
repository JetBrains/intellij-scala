package org.jetbrains.sbt.project.fixture

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.java.LanguageLevel
import junit.framework.TestCase
import org.jetbrains.plugins.scala.base.TestCaseExt
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.project.RequiresJdk

final class TestProjectJdkHolder(languageLevel: LanguageLevel) {

  private var jdk: Sdk = _

  def configuredJdk: Sdk = {
    if (jdk == null)
      throw new IllegalStateException("Test project JDK is not set up")
    jdk
  }

  def setUp(): Unit = {
    if (jdk == null) {
      jdk = SmartJDKLoader.getOrCreateJDK(languageLevel)
      ensureJdkRegisteredInGlobalJdkTable()
    }
  }

  // TODO: in a separate commit make this the default behavior and see if anything fails.
  //  It would be a sane default for the tests.
  def setAsProjectJdk(project: Project): Unit = inWriteAction {
    ProjectRootManager.getInstance(project).setProjectSdk(configuredJdk)
  }

  /**
   * Must be called explicitly from the test `tearDown`.
   *
   * Do not rely on [[com.intellij.openapi.Disposable]] because disposal through the test root disposable happens too late:
   * heavy fixture SDK leak checks run before that disposal path removes this SDK from the global JDK table.
   */
  def tearDown(): Unit = {
    if (jdk != null) {
      inWriteAction {
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.removeJdk(jdk)
      }
      jdk = null
    }
  }

  private def ensureJdkRegisteredInGlobalJdkTable(): Unit = inWriteAction {
    val jdkTable = ProjectJdkTable.getInstance()
    if (!jdkTable.getAllJdks.contains(jdk)) {
      jdkTable.addJdk(jdk)
    }
  }
}

object TestProjectJdkHolder {

  def defaultProjectJdkLanguageLevel(testCase: TestCase): LanguageLevel = {
    val requiresJdkAnnotation = testCase.findTestAnnotation[RequiresJdk]
    val requiredJdk = requiresJdkAnnotation.map(_.value())
    requiredJdk.getOrElse(LanguageLevel.JDK_11)
  }
}
