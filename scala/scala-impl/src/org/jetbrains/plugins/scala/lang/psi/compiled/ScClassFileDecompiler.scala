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

  override def createFileViewProvider(file: VirtualFile,
                                      manager: PsiManager,
                                      eventSystemEnabled: Boolean): ScSingleRootFileViewProvider = file match {
    case DecompilationResult(sourceName, sourceText) =>
      new ScalaClassFileViewProvider(manager, file, eventSystemEnabled, Some(sourceName)) {
        override val getContents: String = sourceText
      }
    case _ => new NonScalaClassFileViewProvider(manager, file, eventSystemEnabled)
  }
}

object ScClassFileDecompiler {

  import impl.{ScalaFileImpl, ScalaPsiElementFactory}

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

  sealed abstract class ScSingleRootFileViewProvider(manager: PsiManager,
                                                     file: VirtualFile,
                                                     eventSystemEnabled: Boolean)
    extends SingleRootFileViewProvider(manager, file, eventSystemEnabled, ScalaLanguage.INSTANCE)

  private class ScalaClassFileViewProvider protected(manager: PsiManager,
                                                     file: VirtualFile,
                                                     eventSystemEnabled: Boolean,
                                                     sourceName: Some[String])
    extends ScSingleRootFileViewProvider(manager, file, eventSystemEnabled) {

    override def createFile(project: Project,
                            virtualFile: VirtualFile,
                            fileType: FileType) =
      new ScalaFileImpl(this, sourceName = sourceName)

    override def getContents: String = getVirtualFile match {
      case DecompilationResult(_, sourceText) => sourceText
    }

    override def createCopy(copy: VirtualFile) =
      new ScalaClassFileViewProvider(getManager, copy, false, sourceName)
  }

  private final class NonScalaClassFileViewProvider(manager: PsiManager,
                                                    file: VirtualFile,
                                                    eventSystemEnabled: Boolean)
    extends ScSingleRootFileViewProvider(manager, file, eventSystemEnabled) {

    override def createFile(project: Project,
                            virtualFile: VirtualFile,
                            fileType: FileType): Null = null

    override def getContents = ""

    override def createCopy(copy: VirtualFile) =
      new NonScalaClassFileViewProvider(getManager, copy, false)
  }

}
