package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.junit.Assert.{assertEquals, assertTrue}

import scala.jdk.CollectionConverters.ListHasAsScala

final class Scala3PsiDocumentationTargetProviderTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testOverloadedExtensionImportProvidesOneDocumentationTargetPerDeclaration(): Unit = {
    configureScalaFromFileText(
      s"""class User
         |class Project
         |class Domain
         |
         |object Definitions:
         |  extension (target: User) def present: String = "user"
         |  extension (target: Project) def present: String = "project"
         |  extension (target: Domain) def present: String = "domain"
         |
         |object Usage:
         |  import Definitions.pre${CARET}sent
         |""".stripMargin
    )

    val targets = IdeDocumentationTargetProvider
      .getInstance(getProject)
      .documentationTargets(getEditor, getFile, getEditor.getCaretModel.getOffset)
      .asScala
      .collect { case target: PsiElementDocumentationTarget => target.getTargetElement }

    assertEquals(3, targets.size)
    assertTrue(targets.forall(_.isInstanceOf[ScFunction]))
    assertTrue(targets.forall(_.isPhysical))
  }
}
