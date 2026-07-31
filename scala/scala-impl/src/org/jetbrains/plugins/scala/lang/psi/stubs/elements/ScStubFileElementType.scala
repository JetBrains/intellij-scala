package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.Language
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.stubs._
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.psi.tree.{IElementType, IFileElementType, IStubFileElementType, TemplateLanguageStubBaseVersion}
import com.intellij.psi.{PsiClass, PsiFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClassFileDecompiler
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFileStub

/**
 * A plain [[IFileElementType]] loadable on the Remote Development frontend. Stub support is provided independently:
 *  - stub building / versioning via [[org.jetbrains.plugins.scala.lang.psi.stubs.ScalaLanguageStubDefinitionBase]]
 *  - file stub serialization via [[org.jetbrains.plugins.scala.lang.psi.stubs.ScalaFileStubSerializer]]
 */
//noinspection TypeAnnotation
class ScStubFileElementType(debugName: String, language: Language)
  extends IFileElementType(debugName, language) {

  /** External id used by the file stub serializer (formerly [[IStubFileElementType.getExternalId]]). */
  val stubExternalId: String = debugName

  /**
   * Stub serialization format version for this language
   */
  def stubVersion: Int =
    ScStubFileElementType.getStubVersion(language) + ScClassFileDecompiler.ScClsStubBuilder.getStubVersion

  def shouldBuildStubFor(file: VirtualFile): Boolean =
    file.getFileSystem.getProtocol != StandardFileSystems.JAR_PROTOCOL

  def stubBuilder = new ScFileStubBuilderImpl

  /** Used by [[org.jetbrains.plugins.scala.lang.psi.stubs.ScalaFileStubSerializer]] on deserialize. */
  def createFileStub(@Nullable file: ScalaFile): ScFileStub = new ScFileStubImpl(file)

  protected class ScFileStubBuilderImpl extends DefaultStubBuilder {

    override def buildStubTree(file: PsiFile) =
      super.buildStubTree(file).asInstanceOf[PsiFileStubImpl[_ <: PsiFile]]

    protected override final def createStubForFile(file: PsiFile): PsiFileStubImpl[_ <: PsiFile] =
      file.getViewProvider.getPsi(getLanguage) match {
        case scalaFile: ScalaFile => new ScFileStubImpl(scalaFile)
        case _ => new PsiFileStubImpl(file)
      }
  }

  protected final class ScFileStubImpl(@Nullable file: ScalaFile)
    extends PsiFileStubImpl(file) with ScFileStub {

    override def getType: IStubFileElementType[_] =
      throw new UnsupportedOperationException("Use getFileElementType() instead")

    override def getFileElementType: IElementType = ScStubFileElementType.this

    override def getElementType: IElementType = getFileElementType

    override def getClasses: Array[PsiClass] = getChildrenByType(
      TokenSets.TYPE_DEFINITIONS,
      PsiClass.ARRAY_FACTORY
    )
  }

}

object ScStubFileElementType {

  def apply(language: Language) = new ScStubFileElementType(
    s"${language.getDisplayName.toLowerCase} FILE".replace(' ', '.'),
    language
  )

  private def getStubVersion(language: Language): Int = language match {
    case _: TemplateLanguage =>
      //noinspection ApiStatus,UnstableApiUsage
      BaseStubVersion + TemplateLanguageStubBaseVersion.getVersion
    case _ => BaseStubVersion
  }

  /**
   * Should be incremented each time when stub tree changes (e.g. elements added/removed,
   * element serialization/deserialization changes)
   */
  private val BaseStubVersion = 2
}
