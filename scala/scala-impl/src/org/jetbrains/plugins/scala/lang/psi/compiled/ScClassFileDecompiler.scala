package org.jetbrains.plugins.scala.lang.psi.compiled

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.lang.{Language, LanguageParserDefinitions}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{FileViewProvider, PsiFile, PsiFileFactory, PsiManager, SingleRootFileViewProvider, compiled, stubs}
import com.intellij.util.indexing.FileContent
import org.jetbrains.plugins.scala.lang.parser.{Scala3ParserDefinition, ScalaParserDefinition}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.plugins.scala.tasty.TastyFileType

final class ScClassFileDecompiler extends compiled.ClassFileDecompilers.Full {

  import ScClassFileDecompiler._

  override def accepts(file: VirtualFile): Boolean =
    isTasty(file) ||
      hasTasty(file) || // Accept .class files that have .tasty to subsequently hide them (to avoid .tasty + .class duplication).
      topLevelScalaClassFor(file).nonEmpty

  override def getStubBuilder: ScClsStubBuilder.type = ScClsStubBuilder

  override def createFileViewProvider(file: VirtualFile,
                                      manager: PsiManager,
                                      eventSystemEnabled: Boolean): FileViewProvider = {
    if (!isTasty(file) && hasTasty(file)) {
      new NonScalaClassFileViewProvider(manager, file, eventSystemEnabled, ScalaLanguage.INSTANCE)
    } else {
      createFileViewProviderImpl(manager, file, eventSystemEnabled, ScalaLanguage.INSTANCE)
    }
  }
}

object ScClassFileDecompiler {

  import DecompilationResult._

  object ScClsStubBuilder extends compiled.ClsStubBuilder {

    override val getStubVersion = 486

    override def buildFileStub(content: FileContent): stubs.PsiFileStubImpl[_ <: PsiFile] = {
      val psiFile = decompiledScalaFile(content)
      psiFile
        .map((if (isTasty(content.getFile)) stub3Builder else stub2Builder).buildStubTree)
        .orNull
    }

    private def stub2Builder =
      LanguageParserDefinitions.INSTANCE
        .forLanguage(ScalaLanguage.INSTANCE)
        .asInstanceOf[ScalaParserDefinition]
        .getFileNodeType
        .getBuilder

    private def stub3Builder =
      LanguageParserDefinitions.INSTANCE
        .forLanguage(Scala3Language.INSTANCE)
        .asInstanceOf[Scala3ParserDefinition]
        .getFileNodeType
        .getBuilder
  }

  private def decompiledScalaFile(content: FileContent): Option[PsiFile] = {
    val file = content.getFile
    //TODO: the `isTopLevelScalaClass` check seems to be causing SCL-24273
    // we need to carefully fix it ensuring no performance regression reappears SCL-15202
    if (isTasty(file) || (!hasTasty(file) && isTopLevelScalaClass(file))) {
      val nameAndText = sourceNameAndText(file, content.getContent)
      nameAndText.map { case (sourceName, sourceText) =>
        val language = if (isTasty(file)) Scala3Language.INSTANCE else ScalaLanguage.INSTANCE
        val psiFile = PsiFileFactory.getInstance(content.getProject).createFileFromText(
          sourceName,
          language,
          sourceText,
          true,
          false,
          false,
          file
        )
        psiFile
      }
    }
    else None
  }


  def createFileViewProviderImpl(manager: PsiManager, file: VirtualFile,
                                 eventSystemEnabled: Boolean, language: Language): FileViewProvider =
    tryDecompile(file) match {
      case Some(decompilationResult) =>
        new ScClsFileViewProvider(decompilationResult)(manager, file, eventSystemEnabled, language)
      case _ =>
        new NonScalaClassFileViewProvider(manager, file, eventSystemEnabled, language)
    }

  private final class NonScalaClassFileViewProvider(manager: PsiManager, file: VirtualFile,
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

  //TODO: see comments inside, it seems like this logic is not relevant.
  // It's perfectly fine to have 2 top-level classes named `SourceInfo` and ``SourceInfo$Impl` (Impl is NOT the inner class in this case)
  private def topLevelScalaClassFor(file: VirtualFile): Option[String] = {
    val extension = file.getExtension
    val classFileExtension = JavaClassFileType.INSTANCE.getDefaultExtension

    extension match {
      case "sig" => Some(file.getNameWithoutExtension)
      case `classFileExtension` =>
        val containingDirectory = file.getParent
        if (containingDirectory != null) {
          def hasDecompilableChild(nameWithoutExtension: String) =
            containingDirectory.findChild(nameWithoutExtension + '.' + classFileExtension) match {
              case null => false
              case child => tryDecompile(child).isDefined
            }

          val fileName = file.getNameWithoutExtension
          val dollarPrefixes = new DollarSeparatedFileNamePrefixesIterator(fileName)
          val siblingPrefixClassFileName = dollarPrefixes.find { prefix =>
            // TODO: this check seems to be incorrect
            //  It's perfectly fine to have 2 top-level classes named `SourceInfo` and ``SourceInfo$Impl`
            //  and `Impl` won't be the inner class in this case)
            !prefix.endsWith("$") &&
              hasDecompilableChild(prefix)
          }
          siblingPrefixClassFileName
        }
        else None
      case _ => None
    }
  }

  private def isTasty(file: VirtualFile) =
    file.getExtension == TastyFileType.getDefaultExtension

  private def hasTasty(file: VirtualFile): Boolean = {
    val directory = file.getParent

    if (directory == null) {
      return false
    }

    val dollarPrefixes = new DollarSeparatedFileNamePrefixesIterator(file.getNameWithoutExtension)
    dollarPrefixes.exists { name =>
      val child = directory.findChild(s"$name.${TastyFileType.getDefaultExtension}")
      child != null
    }
  }

  //Examples:
  //   A     -> A
  //   A$    -> A, A$
  //   A$B   -> A, A$B
  //   A$B$  -> A, A$B, A$B$
  //   A$B$C -> A, A$B, A$B$C
  private[this] class DollarSeparatedFileNamePrefixesIterator(private val fileName: String) extends Iterator[String] {

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
