package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VirtualFile, newvfs}
import com.intellij.psi.compiled.{ClassFileDecompilers, ClsStubBuilder}
import com.intellij.psi.{PsiFile, PsiManager, SingleRootFileViewProvider}
import com.intellij.util.indexing.FileContent
import org.jetbrains.plugins.scala.lang.psi.{impl, stubs}

final class ScClassFileDecompiler extends ClassFileDecompilers.Full {

  import ScClassFileDecompiler._

  override def accepts(file: VirtualFile): Boolean = file.isAcceptable

  override def getStubBuilder: ClsStubBuilder = ScClsStubBuilder

  override def createFileViewProvider(file: VirtualFile,
                                      manager: PsiManager,
                                      physical: Boolean): ScSingleRootFileViewProvider =
    file.sourceContent
      .fold(new NonScalaClassFileViewProvider(manager, file, physical): ScSingleRootFileViewProvider) { contents =>
        new ScalaClassFileViewProvider(manager, file, physical) {
          override val getContents: String = contents
        }
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
              createScalaFile(file, content.getContent)(content.getProject)
            }
      }

    private def createScalaFile(virtualFile: VirtualFile, content: Array[Byte])
                               (implicit project: Project): ScalaFileImpl =
      virtualFile.decompile(content) match {
        case DecompilationResult(_, _, sourceText) =>
          val result = ScalaPsiElementFactory.createScalaFileFromText(sourceText)
            .asInstanceOf[ScalaFileImpl]
          result.virtualFile = virtualFile
          result
      }
  }

  sealed abstract class ScSingleRootFileViewProvider(manager: PsiManager,
                                                     file: VirtualFile,
                                                     physical: Boolean)
    extends SingleRootFileViewProvider(manager, file, physical, ScalaLanguage.INSTANCE)

  private class ScalaClassFileViewProvider protected(manager: PsiManager,
                                                     file: VirtualFile,
                                                     physical: Boolean)
    extends ScSingleRootFileViewProvider(manager, file, physical) {

    override def createFile(project: Project,
                            virtualFile: VirtualFile,
                            fileType: FileType): PsiFile = {
      val file = new ScalaFileImpl(this)
      file.virtualFile = virtualFile
      file
    }

    override def getContents: String = getVirtualFile.decompile() match {
      case DecompilationResult(_, _, sourceText) => sourceText
    }

    override def createCopy(copy: VirtualFile) =
      new ScalaClassFileViewProvider(getManager, copy, false)
  }

  private final class NonScalaClassFileViewProvider(manager: PsiManager,
                                                    file: VirtualFile,
                                                    physical: Boolean)
    extends ScSingleRootFileViewProvider(manager, file, physical) {

    override def createFile(project: Project,
                            virtualFile: VirtualFile,
                            fileType: FileType): PsiFile = null

    override def getContents = ""

    override def createCopy(copy: VirtualFile) =
      new NonScalaClassFileViewProvider(getManager, copy, false)
  }

}
