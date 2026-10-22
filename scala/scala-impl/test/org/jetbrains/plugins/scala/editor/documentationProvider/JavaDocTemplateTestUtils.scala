package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util.Using

private[scala] object JavaDocTemplateTestUtils {
  def enableJavaDocMethodTemplate(project: Project, disposable: Disposable): Unit = {
    Registry.get("java.javadoc.use.templates").setValue(true, disposable)

    // Bundled file templates are not loaded automatically in the test environment.
    val manager = FileTemplateManager.getInstance(project)
    val originalTemplates = manager.getAllCodeTemplates.toSeq.asJava
    Disposer.register(disposable, () => manager.setTemplates(FileTemplateManager.CODE_TEMPLATES_CATEGORY, originalTemplates))
    val template = new CustomFileTemplate("JavaDoc Method", "java")
    val text = Using.resource(classOf[JavaDocumentationProvider].getResourceAsStream("/fileTemplates/code/JavaDoc Method.java.ft")) { stream =>
      new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    }
    template.setText(text)
    manager.setTemplates(FileTemplateManager.CODE_TEMPLATES_CATEGORY, (manager.getAllCodeTemplates.toSeq :+ template).asJava)
  }
}
