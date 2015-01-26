package org.jetbrains.plugins.hocon.lang

import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory}

class HoconFileTypeFactory extends FileTypeFactory {
  def createFileTypes(consumer: FileTypeConsumer) =
    consumer.consume(HoconFileType, HoconFileType.DefaultExtension)
}
