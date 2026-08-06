package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.{BinaryContent, CompiledClass}

import java.nio.file.{Files, Path}

// TODO expect future JPS API to load the generated file content lazily (on demand)
class LazyCompiledClass(outputFile: Path, sourceFile: Path, className: String)
        extends CompiledClass(outputFile.toFile, sourceFile.toFile, className, new BinaryContent(Array.empty)) {

  private var loadedContent: Option[BinaryContent] = None
  private var contentIsSet = false

  override def getContent: BinaryContent = {
    if (contentIsSet) super.getContent else loadedContent.getOrElse {
      val content = new BinaryContent(Files.readAllBytes(outputFile))
      loadedContent = Some(content)
      content
    }
  }

  override def setContent(content: BinaryContent): Unit = {
    super.setContent(content)
    loadedContent = None
    contentIsSet = true
  }
}
