package intellijhocon
package lang

import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory}

class HoconFileTypeFactory extends FileTypeFactory {
  def createFileTypes(consumer: FileTypeConsumer) =
    consumer.consume(HoconLanguageFileType, HoconLanguageFileType.DefaultExtension)
}
