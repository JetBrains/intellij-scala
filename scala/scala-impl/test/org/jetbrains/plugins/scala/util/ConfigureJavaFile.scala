package org.jetbrains.plugins.scala.util

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.scala.extensions.inWriteAction

import scala.annotation.nowarn

object ConfigureJavaFile {
  def configureJavaFile(fileText: String,
                        className: String,
                        packageName: String = null): Unit = inWriteAction {
    @nowarn("cat=deprecation") // LightPlatformTestCase is deprecated in favor of JUnit 5 (IJPL-233558)
    val root = LightPlatformTestCase.getSourceRoot match {
      case sourceRoot if packageName == null => sourceRoot
      case sourceRoot => sourceRoot.createChildDirectory(null, packageName)
    }

    val file = root.createChildData(null, className + ".java")
    VfsUtil.saveText(file, normalize(fileText))
  }

  private def normalize(text: String, trimContent: Boolean = true): String = {
    val result = text.stripMargin.replace("\r", "")
    if (trimContent) result.trim else result
  }
}
