package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs._
import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.util.indexing.FileContent
import org.jetbrains.plugins.scala.lang.psi.{impl, stubs}

object ScClsStubBuilder extends ClsStubBuilder {

  override val getStubVersion = 314

  private[decompiler] val DecompilerFileAttribute = new newvfs.FileAttribute(
    "_is_scala_compiled_new_key_",
    getStubVersion,
    true
  )

  override def buildFileStub(content: FileContent): stubs.ScFileStub =
    content.getFile match {
      case file if isInnerClass(file) => null
      case file =>
        LanguageParserDefinitions.INSTANCE
          .forLanguage(ScalaLanguage.INSTANCE)
          .asInstanceOf[lang.parser.ScalaParserDefinition]
          .getFileNodeType
          .getBuilder
          .buildStubTree {
            createScalaFile(file, content.getContent)(content.getProject)
          }
    }

  private def createScalaFile(file: VirtualFile, content: Array[Byte])
                             (implicit project: Project) =
    impl.ScalaPsiElementFactory.createScalaFileFromText {
      decompile(file)(content).sourceText
    } match {
      case scalaFile: impl.ScalaFileImpl =>
        scalaFile.virtualFile = file
        scalaFile
    }

  private def isInnerClass(file: VirtualFile): Boolean =
    file.getParent match {
      case null => false
      case parent => !file.isClass && parent.isInner(file.getNameWithoutExtension)
    }

  private implicit class VirtualFileExt(private val virtualFile: VirtualFile) extends AnyVal {

    import reflect.NameTransformer.decode

    def isInner(name: String): Boolean = {
      if (name.endsWith("$") && contains(name, name.length - 1)) {
        return false //let's handle it separately to avoid giving it for Java.
      }
      isInner(decode(name), 0)
    }

    private def isInner(name: String, from: Int): Boolean = {
      val index = name.indexOf('$', from)

      val containsPart = index > 0 && contains(name, index)
      index != -1 && (containsPart || isInner(name, index + 1))
    }

    private def contains(name: String, endIndex: Int): Boolean =
      virtualFile.getChildren.exists { child =>
        child.isClass &&
          decode(child.getNameWithoutExtension) != name.substring(0, endIndex)
      }

    def isClass: Boolean = virtualFile.getExtension == "class"
  }

}
