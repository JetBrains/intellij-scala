package org.jetbrains.plugins.scala
package worksheet
package codeInspection
package typeAnnotation

import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG, SELECTION_START_TAG}
import org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspectionTest

class WorksheetAnnotationInspectionTest extends TypeAnnotationInspectionTest {

  override protected val fileType: WorksheetFileType.type = WorksheetFileType

  private var excludeInDialectSources: Boolean = _

  protected override def setUp(): Unit = {
    super.setUp()

    val settings = getScalaCodeStyleSettings

    excludeInDialectSources = settings.TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES
    settings.TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES = false
  }

  override def tearDown(): Unit = {
    getScalaCodeStyleSettings.TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES = excludeInDialectSources

    super.tearDown()
  }

  def testPublicValue(): Unit = testQuickFix(
    s"""val ${SELECTION_START_TAG}string$SELECTION_END_TAG = "".substring(0)""",
    s"""val string: String = "".substring(0)"""
  )

}
