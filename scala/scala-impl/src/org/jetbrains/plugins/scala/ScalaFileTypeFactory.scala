package org.jetbrains.plugins.scala

import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory}

final class ScalaFileTypeFactory extends FileTypeFactory {

  override def createFileTypes(consumer: FileTypeConsumer): Unit = consumer.consume(ScalaFileType.INSTANCE)
}
