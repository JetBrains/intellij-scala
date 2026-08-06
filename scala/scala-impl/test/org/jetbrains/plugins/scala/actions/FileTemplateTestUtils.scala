package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

object FileTemplateTestUtils {

  /**
   * Creates a template called "File Header.java" which is references in all scala file templates
   *
   * @todo why the java header is used in Scala? It's also used in Kotlin.
   *       It seems some legacy thing.
   *       Ideally there should be separate headers for Scala and Kotlin.
   *       But due to it existed for > 15 years now it seems like no one cares and it's probably not a widely used feature
   */
  def initFileHeaderTemplate(
    project: Project,
    testDisposable: Disposable,
    isEmpty: Boolean = true
  ): Unit = {
    val templateManager = FileTemplateManager.getInstance(project).asInstanceOf[FileTemplateManagerImpl]
    val templateText =
      if (isEmpty) ""
      else
        """
          |/**
          | * Created by ${USER} on ${DATE}.
          | */
          |""".stripMargin

    templateManager.setDefaultFileIncludeTemplateTextTemporarilyForTest(
      FileTemplateManager.FILE_HEADER_TEMPLATE_NAME,
      templateText,
      testDisposable
    )
  }
}
