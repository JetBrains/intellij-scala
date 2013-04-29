package org.jetbrains.sbt
package language

import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory}

/**
 * @author Pavel Fatin
 */
class SbtFileTypeFactory extends FileTypeFactory {
  def createFileTypes(consumer: FileTypeConsumer) {
    consumer.consume(SbtFileType)
  }
}
