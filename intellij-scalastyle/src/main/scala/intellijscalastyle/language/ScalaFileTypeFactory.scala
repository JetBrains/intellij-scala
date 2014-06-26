package intellijscalastyle.language

import com.intellij.openapi.fileTypes.{FileTypeConsumer, FileTypeFactory}
import org.jetbrains.plugins.scala.ScalaFileType

class ScalaFileTypeFactory extends FileTypeFactory {
  override def createFileTypes(consumer: FileTypeConsumer): Unit = ???
}
