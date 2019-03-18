package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VirtualFile, newvfs}
import com.intellij.psi.{PsiManager, SingleRootFileViewProvider, compiled}
import com.intellij.util.indexing.FileContent

final class ScClassFileDecompiler extends compiled.ClassFileDecompilers.Full {

  import ScClassFileDecompiler._

  override def accepts(file: VirtualFile): Boolean = file.isAcceptable

  override def getStubBuilder: ScClsStubBuilder.type = ScClsStubBuilder

  override def createFileViewProvider(file: VirtualFile, manager: PsiManager,
                                      eventSystemEnabled: Boolean): SingleRootFileViewProvider =
    createFileViewProvider(eventSystemEnabled)(manager, file)

  private def createFileViewProvider(eventSystemEnabled: Boolean)
                                    (implicit manager: PsiManager, file: VirtualFile) =
    file match {
      case DecompilationResult(sourceName, contents) => new ScClsFileViewProvider(sourceName, contents, eventSystemEnabled)
      case _ => new NonScalaClassFileViewProvider(eventSystemEnabled)
    }

}

object ScClassFileDecompiler {

  import impl.{ScFileViewProviderFactory, ScalaFileImpl, ScalaPsiElementFactory}

  object ScClsStubBuilder extends compiled.ClsStubBuilder {

    override val getStubVersion = 315

    // Underlying VFS implementation may not support attributes (e.g. Upsource's file system).
    private[compiled] val DecompilerFileAttribute = ApplicationManager.getApplication match {
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
        case DecompilationResult(sourceName, sourceText) =>
          val scalaFile = ScalaPsiElementFactory.createScalaFileFromText(sourceText)(content.getProject)
            .asInstanceOf[ScalaFileImpl]
          scalaFile.sourceName = Some(sourceName)

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

  import ScFileViewProviderFactory.ScFileViewProvider

  private final class ScClsFileViewProvider(sourceName: String,
                                            override val getContents: String,
                                            eventSystemEnabled: Boolean)
                                           (implicit manager: PsiManager, file: VirtualFile)
    extends ScFileViewProvider(eventSystemEnabled) {

    override def createFile(project: Project,
                            file: VirtualFile,
                            fileType: FileType) =
      new ScalaFileImpl(this, sourceName = Some(sourceName))

    override protected def createCopy(eventSystemEnabled: Boolean)
                                     (implicit manager: PsiManager, file: VirtualFile) =
      new ScClsFileViewProvider(sourceName, getContents, eventSystemEnabled)
  }

  private final class NonScalaClassFileViewProvider(eventSystemEnabled: Boolean)
                                                   (implicit manager: PsiManager, file: VirtualFile)
    extends ScFileViewProvider(eventSystemEnabled) {

    override def createFile(project: Project,
                            file: VirtualFile,
                            fileType: FileType): Null = null

    override def getContents = ""

    override protected def createCopy(eventSystemEnabled: Boolean)
                                     (implicit manager: PsiManager, file: VirtualFile) =
      new NonScalaClassFileViewProvider(eventSystemEnabled)
  }

}
