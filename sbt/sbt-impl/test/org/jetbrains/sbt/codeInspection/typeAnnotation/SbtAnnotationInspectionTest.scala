package org.jetbrains.sbt
package codeInspection
package typeAnnotation

import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG, SELECTION_START_TAG}
import org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspectionTest
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.language.SbtFileType

import java.{util => ju}

class SbtAnnotationInspectionTest extends TypeAnnotationInspectionTest with MockSbt_1_0 {

  override protected val fileType = SbtFileType

  override implicit val sbtVersion: Version = Sbt.LatestVersion

  private var excludeWhenTypeIsStable: Boolean = _
  private var excludeInDialectSources: Boolean = _
  private var excludeWhenTypeMatches: ju.Set[String] = _

  protected override def setUp(): Unit = {
    super.setUp()

    SbtHighlightingUtil.enableHighlightingOutsideBuildModule(getProject)

    val settings = getScalaCodeStyleSettings
    import settings._

    excludeWhenTypeIsStable = TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_STABLE
    TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_STABLE = false

    excludeInDialectSources = TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES
    TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES = false

    excludeWhenTypeMatches = TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES
    TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES = ju.Collections.emptySet()
  }

  override def tearDown(): Unit = {
    val settings = getScalaCodeStyleSettings
    import settings._

    TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_STABLE = excludeWhenTypeIsStable
    TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES = excludeInDialectSources
    TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES = excludeWhenTypeMatches

    super.tearDown()
  }

  def testPublicValue(): Unit = testQuickFix(
    s"lazy val ${SELECTION_START_TAG}project$SELECTION_END_TAG = Project(null, null)",
    "lazy val project: Project = Project(null, null)"
  )
}
