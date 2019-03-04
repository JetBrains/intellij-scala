package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
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
                                      physical: Boolean): ScSingleRootFileViewProvider = file match {
    case DecompilationResult(sourceName, sourceText) =>
      new ScalaClassFileViewProvider(manager, file, physical, sourceName) {
        override val getContents: String = sourceText
      }
    case _ => new NonScalaClassFileViewProvider(manager, file, physical)
  }
}

object ScClassFileDecompiler {

  import impl.{ScalaFileImpl, ScalaPsiElementFactory}

  object ScClsStubBuilder extends ClsStubBuilder {

    override val getStubVersion = 314

    // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
    private[decompiler] val DecompilerFileAttribute = ApplicationManager.getApplication match {
      // The following check is hardly bulletproof, however (currently) there is no API to query that
      case application if application.getClass.getSimpleName.contains("Upsource") => None
      case application =>
        debugger.ScalaJVMNameMapper(application)

        val attribute = new newvfs.FileAttribute(
          "_is_scala_compiled_new_key_",
          getStubVersion,
          true
        )
        Some(attribute)
    }

    override def buildFileStub(content: FileContent): stubs.ScFileStub = {
      implicit val bytes: Array[Byte] = content.getContent

      content.getFile match {
        case file if file.isInnerClass => null
        case file@DecompilationResult(sourceName, sourceText) =>
          val scalaFile = ScalaPsiElementFactory.createScalaFileFromText(sourceText)(content.getProject)
            .asInstanceOf[ScalaFileImpl]
          scalaFile.sourceName = sourceName
          scalaFile.virtualFile = file

          LanguageParserDefinitions.INSTANCE
            .forLanguage(ScalaLanguage.INSTANCE)
            .asInstanceOf[lang.parser.ScalaParserDefinition]
            .getFileNodeType
            .getBuilder
            .buildStubTree(scalaFile)
        case _ => null
      }
    }
  }

  sealed abstract class ScSingleRootFileViewProvider(manager: PsiManager,
                                                     file: VirtualFile,
                                                     physical: Boolean)
    extends SingleRootFileViewProvider(manager, file, physical, ScalaLanguage.INSTANCE)

  private class ScalaClassFileViewProvider protected(manager: PsiManager,
                                                     file: VirtualFile,
                                                     physical: Boolean,
                                                     sourceName: String)
    extends ScSingleRootFileViewProvider(manager, file, physical) {

    override def createFile(project: Project,
                            virtualFile: VirtualFile,
                            fileType: FileType): PsiFile = {
      val file = new ScalaFileImpl(this)
      file.sourceName = sourceName
      file.virtualFile = virtualFile
      file
    }

    override def getContents: String = getVirtualFile match {
      case DecompilationResult(_, sourceText) => sourceText
    }

    override def createCopy(copy: VirtualFile) =
      new ScalaClassFileViewProvider(getManager, copy, false, sourceName)
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
