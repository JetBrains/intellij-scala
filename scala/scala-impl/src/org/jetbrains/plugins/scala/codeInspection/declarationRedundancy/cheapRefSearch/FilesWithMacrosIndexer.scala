package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.{DataIndexer, FileBasedIndex, FileBasedIndexExtension, FileContent, ID}
import com.intellij.util.io.{KeyDescriptor, VoidDataExternalizer}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.io.{DataInput, DataOutput}
import java.util
import scala.annotation.unused
import scala.jdk.CollectionConverters.CollectionHasAsScala

@unused("registered in scala-plugin-common.xml")
final class FilesWithMacrosIndexer extends FileBasedIndexExtension[String, Void] {
  override def getName: ID[String, Void] = FilesWithMacrosIndexer.Id

  override def getInputFilter: FileBasedIndex.InputFilter = file =>
    file.isInLocalFileSystem && file.getFileType.is[ScalaFileType]

  override def dependsOnFileContent(): Boolean = true

  override def getIndexer: DataIndexer[String, Void, FileContent] = (inputData: FileContent) => {
    inputData.getPsiFile match {
      case file: ScalaFile =>
        val map = new util.HashMap[String, Void]()
        if (file.getText.contains("import scala.reflect.macros")) map.put(file.getVirtualFile.getPath, null)
        map
      case _ => java.util.Collections.emptyMap()
    }
  }

  override def getKeyDescriptor: KeyDescriptor[String] = new KeyDescriptor[String] {
    override def getHashCode(value: String): Int = value.hashCode()

    override def isEqual(val1: String, val2: String): Boolean = val1 == val2

    override def save(out: DataOutput, value: String): Unit = out.writeUTF(value)

    override def read(in: DataInput): String = in.readUTF()
  }

  override def getValueExternalizer: VoidDataExternalizer = VoidDataExternalizer.INSTANCE

  override def getVersion: Int = 1
}

object FilesWithMacrosIndexer {
  val Id: ID[String, Void] = ID.create("FilesWithMacrosIndex")

  def filesWithMacros(project: Project): Seq[String] =
    FileBasedIndex.getInstance().getAllKeys(Id, project).asScala.toSeq

}
