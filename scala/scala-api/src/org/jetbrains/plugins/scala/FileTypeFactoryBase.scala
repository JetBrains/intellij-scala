package org.jetbrains.plugins.scala

import com.intellij.openapi.fileTypes.{FileType, FileTypeConsumer, FileTypeFactory}

abstract class FileTypeFactoryBase(protected val fileType: FileType) extends FileTypeFactory {

  override def createFileTypes(consumer: FileTypeConsumer): Unit = consumer.consume(fileType)
}
