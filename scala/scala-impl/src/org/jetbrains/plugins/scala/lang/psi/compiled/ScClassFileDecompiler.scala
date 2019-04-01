package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import com.intellij.lang.{Language, LanguageParserDefinitions}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.{VirtualFile, newvfs}
import com.intellij.psi.{PsiFile, PsiFileFactory, PsiManager, SingleRootFileViewProvider, compiled, stubs}
import com.intellij.util.indexing.FileContent

final class ScClassFileDecompiler extends compiled.ClassFileDecompilers.Full {

  import ScClassFileDecompiler._

  override def accepts(file: VirtualFile): Boolean = file.isScalaCompiledClassFile

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

  import impl.ScFileViewProviderFactory

  object ScClsStubBuilder extends compiled.ClsStubBuilder {

    override val getStubVersion = 316

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

    override def buildFileStub(content: FileContent): stubs.PsiFileStubImpl[_ <: PsiFile] = content match {
      case ScClsStubBuilder(scalaFile) =>
        LanguageParserDefinitions.INSTANCE
          .forLanguage(ScalaLanguage.INSTANCE)
          .asInstanceOf[lang.parser.ScalaParserDefinition]
          .getFileNodeType
          .getBuilder
          .buildStubTree(scalaFile)
      case _ => null
    }

    private def unapply(content: FileContent): Option[PsiFile] = content.getFile match {
      case original if original.isScalaInnerClass => None
      case original => DecompilationResult.unapply(original)(content.getContent).map {
        case (sourceName, sourceText) => PsiFileFactory.getInstance(content.getProject).createFileFromText(
          sourceName,
          ScalaLanguage.INSTANCE,
          sourceText,
          true,
          true,
          false,
          original
        )
      }
    }
  }

  private final class NonScalaClassFileViewProvider(eventSystemEnabled: Boolean)
                                                   (implicit manager: PsiManager, file: VirtualFile)
    extends ScFileViewProviderFactory.ScFileViewProvider(eventSystemEnabled) {

    override def createFile(language: Language): Null = null

    override def getContents = ""

    override protected def createCopy(eventSystemEnabled: Boolean)
                                     (implicit manager: PsiManager, file: VirtualFile) =
      new NonScalaClassFileViewProvider(eventSystemEnabled)
  }

}
