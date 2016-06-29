package org.jetbrains.jps.incremental.scala
package local

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.{BinaryContent, CompiledClass}

/**
 * Nikolay.Tropin
 * 11/18/13
 */
// TODO expect future JPS API to load the generated file content lazily (on demand)
private class LazyCompiledClass(outputFile: File, sourceFile: File, className: String)
        extends CompiledClass(outputFile, sourceFile, className, new BinaryContent(Array.empty)){

  private var loadedContent: Option[BinaryContent] = None
  private var contentIsSet = false

  override def getContent: BinaryContent = {
    if (contentIsSet) super.getContent else loadedContent.getOrElse {
      val content = new BinaryContent(FileUtil.loadFileBytes(outputFile))
      loadedContent = Some(content)
      content
    }
  }

  override def setContent(content: BinaryContent) {
    super.setContent(content)
    loadedContent = None
    contentIsSet = true
  }
}
