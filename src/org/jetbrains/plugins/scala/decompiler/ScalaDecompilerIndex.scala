package org.jetbrains.plugins.scala.decompiler

import com.intellij.util.indexing.FileBasedIndex.InputFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileTypes.StdFileTypes
import java.io.{DataInput, DataOutput}
import com.intellij.util.io.{EnumeratorStringDescriptor, DataExternalizer, KeyDescriptor}
import java.util.{Collections, Map}
import com.intellij.util.indexing._
import com.intellij.psi.search.GlobalSearchScope

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaDecompilerIndex extends FileBasedIndexExtension[String, (String, String)] {
  def getVersion: Int = DecompilerUtil.DECOMPILER_VERSION

  def dependsOnFileContent(): Boolean = true

  def getInputFilter: InputFilter = new InputFilter {
    def acceptInput(file: VirtualFile): Boolean = file.getExtension == StdFileTypes.CLASS.getDefaultExtension
  }

  def getValueExternalizer: DataExternalizer[(String, String)] = {
    new DataExternalizer[(String, String)] {
      def save(out: DataOutput, value: (String, String)) {
        myStringEnumerator.save(out, value._1)
        myStringEnumerator.save(out, value._2)
      }

      def read(in: DataInput): (String, String) = {
        val s1 = myStringEnumerator.read(in)
        val s2 = myStringEnumerator.read(in)
        (s1, s2)
      }

      private val myStringEnumerator: EnumeratorStringDescriptor = new EnumeratorStringDescriptor
    }
  }

  def getKeyDescriptor: KeyDescriptor[String] = new EnumeratorStringDescriptor

  def getIndexer: DataIndexer[String, (String, String), FileContent] =
    new DataIndexer[String, (String, String), FileContent] {
      def map(inputData: FileContent): Map[String, (String, String)] = {
        val vFile = inputData.getFile
        val content = inputData.getContent
        val path = vFile.getPath
        val isScalaFile = DecompilerUtil.isScalaFile(vFile, content, true)
        if (isScalaFile) {
          val decompile = DecompilerUtil.decompile(content, vFile, true)
          Collections.singletonMap(path, (decompile._1, decompile._2))
        } else {
          Collections.singletonMap(path, (ScalaDecompilerIndex.notScala,
            ScalaDecompilerIndex.notScala))
        }
      }
    }

  def getName: ID[String, (String, String)] = ScalaDecompilerIndex.id
}

private[decompiler] object ScalaDecompilerIndex {
  val id: ID[String, (String, String)] = ID.create("scala.decompiler.index")
  
  val notScala = "Not scala"

  val notInScope = "Not in scope"

  def decompile(vFile: VirtualFile, scope: GlobalSearchScope): (String, String) = {
    if (!scope.contains(vFile)) return (notInScope, notInScope)
    val list = FileBasedIndex.getInstance().getValues(id, vFile.getPath, scope)
    if (list.size() != 1) (notScala, notScala)
    else list.get(0)
  }
}