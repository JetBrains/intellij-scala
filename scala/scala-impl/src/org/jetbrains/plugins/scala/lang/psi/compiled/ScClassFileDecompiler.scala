package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.lang.{Language, LanguageParserDefinitions}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiFileFactory, PsiManager, SingleRootFileViewProvider, compiled, stubs}
import com.intellij.util.indexing.FileContent

final class ScClassFileDecompiler extends compiled.ClassFileDecompilers.Full {

  import ScClassFileDecompiler._

  override def accepts(file: VirtualFile): Boolean = topLevelScalaClassFor(file).nonEmpty

  override def getStubBuilder: ScClsStubBuilder.type = ScClsStubBuilder

  override def createFileViewProvider(file: VirtualFile,
                                      manager: PsiManager,
                                      eventSystemEnabled: Boolean): SingleRootFileViewProvider =
    createFileViewProviderImpl(manager, file, eventSystemEnabled, ScalaLanguage.INSTANCE)
}

object ScClassFileDecompiler {

  import DecompilationResult._

  object ScClsStubBuilder extends compiled.ClsStubBuilder {

    override val getStubVersion = 330

    override def buildFileStub(content: FileContent): stubs.PsiFileStubImpl[_ <: PsiFile] =
      decompiledScalaFile(content)
        .map(stubBuilder.buildStubTree)
        .orNull

    private def stubBuilder =
      LanguageParserDefinitions.INSTANCE
        .forLanguage(ScalaLanguage.INSTANCE)
        .asInstanceOf[parser.ScalaParserDefinition]
        .getFileNodeType
        .getBuilder
  }

  private def decompiledScalaFile(content: FileContent): Option[PsiFile] = content.getFile match {
    case original if isTopLevelScalaClass(original) =>
      sourceNameAndText(original, content.getContent).map {
        case (sourceName, sourceText) => PsiFileFactory.getInstance(content.getProject).createFileFromText(
          sourceName,
          ScalaLanguage.INSTANCE,
          sourceText,
          true,
          false,
          false,
          original
        )
      }
    case _ => None
  }


  private[compiled] def createFileViewProviderImpl(manager: PsiManager, file: VirtualFile,
                                                   eventSystemEnabled: Boolean, language: Language) =
    tryDecompile(file) match {
      case Some(decompilationResult) =>
        new ScClsFileViewProvider(decompilationResult)(manager, file, eventSystemEnabled, language)
      case _ =>
        new NonScalaClassFileViewProvider(manager, file, eventSystemEnabled, language)
    }

  private[this] final class NonScalaClassFileViewProvider(manager: PsiManager, file: VirtualFile,
                                                          eventSystemEnabled: Boolean, language: Language)
    extends SingleRootFileViewProvider(manager, file, eventSystemEnabled, language) {

    override def createFile(project: Project,
                            file: VirtualFile,
                            fileType: FileType): Null = null

    override def getContents = ""

    override def createCopy(file: VirtualFile) =
      new NonScalaClassFileViewProvider(getManager, file, eventSystemEnabled = false, getBaseLanguage)
  }

  private def isTopLevelScalaClass(file: VirtualFile): Boolean = topLevelScalaClassFor(file).contains(file.getNameWithoutExtension)

  private def topLevelScalaClassFor(file: VirtualFile): Option[String] = {
    val extension = file.getExtension
    val classFileExtension = JavaClassFileType.INSTANCE.getDefaultExtension

    extension match {
      case "sig" => Some(file.getNameWithoutExtension)
      case `classFileExtension` =>
        file.getParent match {
          case null => None
          case directory =>
            def hasDecompilableChild(nameWithoutExtension: String) =
              directory.findChild(nameWithoutExtension + '.' + classFileExtension) match {
                case null => false
                case child => tryDecompile(child).isDefined
              }

            val fileName = file.getNameWithoutExtension
            new PrefixIterator(fileName).find { prefix =>
              !prefix.endsWith("$") &&
                hasDecompilableChild(prefix)
            }
        }
      case _ => None
    }
  }

  private[this] class PrefixIterator(private val fileName: String) extends Iterator[String] {

    import reflect.NameTransformer._

    private val string = decode(fileName)
    private val stringLength = string.length
    private var endIndex = -1

    override def hasNext: Boolean = endIndex < stringLength

    override def next(): String = endIndex match {
      case `stringLength` => throw new NoSuchElementException("Prefix length equals the string length")
      case current =>
        endIndex = string.indexOf('$', current + 1) match {
          case -1 => stringLength
          case newEndIndex => newEndIndex
        }
        encode(string.substring(0, endIndex))
    }
  }

}
