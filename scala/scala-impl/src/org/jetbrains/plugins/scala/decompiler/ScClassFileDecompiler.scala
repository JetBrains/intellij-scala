package org.jetbrains.plugins.scala
package decompiler

import java.io.IOException

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VirtualFile, newvfs}
import com.intellij.psi.compiled.{ClassFileDecompilers, ClsStubBuilder}
import com.intellij.psi.{PsiFile, PsiManager, SingleRootFileViewProvider}
import com.intellij.util.indexing.FileContent
import org.jetbrains.plugins.scala.lang.psi.{impl, stubs}

import scala.annotation.tailrec

final class ScClassFileDecompiler extends ClassFileDecompilers.Full {

  import ScClassFileDecompiler._

  override def accepts(file: VirtualFile): Boolean =
    isScalaFile(file) || file.canBeProcessed

  override def getStubBuilder: ClsStubBuilder = ScClsStubBuilder

  override def createFileViewProvider(file: VirtualFile, manager: PsiManager, physical: Boolean): ScSingleRootFileViewProvider = {
    val maybeContents = try {
      val decompilationResult = decompile(file)()
      if (decompilationResult.isScala) Some(decompilationResult.sourceText)
      else None
    } catch {
      case _: IOException => None
    }

    ScSingleRootFileViewProvider(physical, maybeContents)(file, manager)
  }
}

object ScClassFileDecompiler {

  import impl.{ScalaFileImpl, ScalaPsiElementFactory}

  object ScClsStubBuilder extends ClsStubBuilder {

    override val getStubVersion = 314

    private[decompiler] val DecompilerFileAttribute = new newvfs.FileAttribute(
      "_is_scala_compiled_new_key_",
      getStubVersion,
      true
    )

    override def buildFileStub(content: FileContent): stubs.ScFileStub =
      content.getFile match {
        case file if file.isInnerClass => null
        case file =>
          LanguageParserDefinitions.INSTANCE
            .forLanguage(ScalaLanguage.INSTANCE)
            .asInstanceOf[lang.parser.ScalaParserDefinition]
            .getFileNodeType
            .getBuilder
            .buildStubTree {
              file.createScalaFile(content.getContent)(content.getProject)
            }
      }

  }

  sealed abstract class ScSingleRootFileViewProvider(physical: Boolean)
                                                    (implicit file: VirtualFile, manager: PsiManager)
    extends SingleRootFileViewProvider(manager, file, physical, ScalaLanguage.INSTANCE)

  private object ScSingleRootFileViewProvider {

    def apply(physical: Boolean, maybeContents: Option[String])
             (implicit file: VirtualFile, manager: PsiManager): ScSingleRootFileViewProvider =
      maybeContents.fold(new NonScalaClassFileViewProvider(physical): ScSingleRootFileViewProvider) {
        new ScalaClassFileViewProvider(physical, _)
      }

    final class ScalaClassFileViewProvider(physical: Boolean, private val contents: String)
                                          (implicit file: VirtualFile, manager: PsiManager)
      extends ScSingleRootFileViewProvider(physical) {

      override def createFile(project: Project, virtualFile: VirtualFile, fileType: FileType): PsiFile = {
        val file = new ScalaFileImpl(this)
        file.virtualFile = virtualFile
        file
      }

      override def getContents: CharSequence = contents match {
        case null => decompile(getVirtualFile)().sourceText
        case _ => contents
      }

      override def createCopy(copy: VirtualFile): SingleRootFileViewProvider =
        new ScalaClassFileViewProvider(false, null)(copy, getManager)
    }

    final class NonScalaClassFileViewProvider(physical: Boolean)
                                             (implicit file: VirtualFile, manager: PsiManager)
      extends ScSingleRootFileViewProvider(physical) {

      override def createFile(project: Project, virtualFile: VirtualFile, fileType: FileType): PsiFile = null

      override def getContents: CharSequence = ""

      override def createCopy(copy: VirtualFile): SingleRootFileViewProvider =
        new NonScalaClassFileViewProvider(false)(copy, getManager)
    }

  }

  private implicit class VirtualFileExt(private val virtualFile: VirtualFile) extends AnyVal {

    import reflect.NameTransformer.decode

    def createScalaFile(content: Array[Byte])
                       (implicit project: Project): ScalaFileImpl =
      ScalaPsiElementFactory.createScalaFileFromText {
        decompile(virtualFile)(content).sourceText
      } match {
        case scalaFile: ScalaFileImpl =>
          scalaFile.virtualFile = virtualFile
          scalaFile
      }

    def isInnerClass: Boolean =
      virtualFile.getParent match {
        case null => false
        case parent => !isClass && parent.isInner(virtualFile.getNameWithoutExtension)
      }

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

    def canBeProcessed: Boolean = {
      val maybeParent = Option(virtualFile.getParent)

      @tailrec
      def go(prefix: String, suffix: String): Boolean = {
        if (!prefix.endsWith("$")) {
          if (maybeParent
            .map(_.findChild(prefix + ".class"))
            .exists(isScalaFile)) return true
        }

        split(suffix) match {
          case Some((suffixPrefix, suffixSuffix)) => go(prefix + "$" + suffixPrefix, suffixSuffix)
          case _ => false
        }
      }

      split(virtualFile.getNameWithoutExtension) match {
        case Some((prefix, suffix)) => go(prefix, suffix)
        case _ => false
      }
    }
  }

  private[this] def split(string: String) = string.indexOf('$') match {
    case -1 => None
    case index => Some(string.substring(0, index), string.substring(index + 1))
  }
}
