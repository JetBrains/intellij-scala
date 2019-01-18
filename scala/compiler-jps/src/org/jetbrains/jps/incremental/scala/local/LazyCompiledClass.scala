package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.util.jar.JarFile

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.{BinaryContent, CompiledClass}
import sbt.internal.inc.JarUtils

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
      val content = new BinaryContent(loadBytes())
      loadedContent = Some(content)
      content
    }
  }

  override def setContent(content: BinaryContent) {
    super.setContent(content)
    loadedContent = None
    contentIsSet = true
  }

  protected def loadBytes(): Array[Byte] = FileUtil.loadFileBytes(outputFile)
}

private class JaredLazyCompiledClass(outputFile: File, sourceFile: File, className: String)
        extends LazyCompiledClass(outputFile, sourceFile, className) {

  override protected def loadBytes(): Array[Byte] = {
    val (jarPath, jarEntry) = JarUtils.ClassInJar.fromFile(outputFile).splitJarReference
    val jarFile = new JarFile(jarPath)
    try {
      val entry = jarFile.getJarEntry(jarEntry)
      val input = jarFile.getInputStream(entry)
      FileUtil.loadBytes(input)
    } finally jarFile.close()
  }

}