package org.jetbrains.sbt

import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory}

/**
 * @author Pavel Fatin
 */
class SbtFileTypeFactory extends FileTypeFactory {
  def createFileTypes(consumer: FileTypeConsumer) {
    consumer.consume(SbtFileType)
  }
}
