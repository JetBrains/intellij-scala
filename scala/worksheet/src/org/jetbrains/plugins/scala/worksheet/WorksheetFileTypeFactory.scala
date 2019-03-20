package org.jetbrains.plugins.scala
package worksheet

import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory}

final class WorksheetFileTypeFactory extends FileTypeFactory {

  override def createFileTypes(consumer: FileTypeConsumer): Unit = consumer.consume(WorksheetFileType)
}
