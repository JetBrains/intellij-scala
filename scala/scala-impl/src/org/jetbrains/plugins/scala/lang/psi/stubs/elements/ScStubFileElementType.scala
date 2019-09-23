package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.Language
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.stubs._
import com.intellij.psi.{PsiClass, PsiElement, PsiFile, tree}

//noinspection TypeAnnotation
class ScStubFileElementType(override val getExternalId: String,
                            language: Language)
  extends tree.IStubFileElementType[ScFileStub]("FILE", language) {

  override final def getStubVersion: Int =
    super.getStubVersion + compiled.ScClassFileDecompiler.ScClsStubBuilder.getStubVersion

  override def shouldBuildStubFor(file: VirtualFile): Boolean =
    file.getFileSystem.getProtocol != StandardFileSystems.JAR_PROTOCOL

  override def getBuilder = new ScFileStubBuilderImpl

  override final def serialize(stub: ScFileStub,
                               dataStream: StubOutputStream): Unit = {}

  override final def deserialize(dataStream: StubInputStream,
                                 parentStub: StubElement[_ <: PsiElement]) =
    new ScFileStubImpl(null)

  override final def indexStub(stub: ScFileStub,
                               sink: IndexSink): Unit = {}

  import api.ScalaFile

  protected class ScFileStubBuilderImpl extends DefaultStubBuilder {

    override def buildStubTree(file: PsiFile) =
      super.buildStubTree(file).asInstanceOf[PsiFileStubImpl[_ <: PsiFile]]

    protected override final def createStubForFile(file: PsiFile): PsiFileStubImpl[_ <: PsiFile] =
      file.getViewProvider.getPsi(getLanguage) match {
        case scalaFile: ScalaFile => new ScFileStubImpl(scalaFile)
        case _ => new PsiFileStubImpl(file)
      }
  }

  protected final class ScFileStubImpl(file: ScalaFile)
    extends PsiFileStubImpl(file) with ScFileStub {

    override def getType = ScStubFileElementType.this

    override def getClasses: Array[PsiClass] = getChildrenByType(
      TokenSets.TYPE_DEFINITIONS,
      PsiClass.ARRAY_FACTORY
    )
  }

}

