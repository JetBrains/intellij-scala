package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.Language
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.stubs._
import com.intellij.psi.{PsiClass, PsiElement, PsiFile, tree}

/**
  * @author ilyas
  */
class ScStubFileElementType(language: Language = ScalaLanguage.INSTANCE)
  extends tree.IStubFileElementType[ScFileStub]("file", language) {

  override final def getStubVersion: Int =
    super.getStubVersion + compiled.ScClassFileDecompiler.ScClsStubBuilder.getStubVersion

  override def shouldBuildStubFor(file: VirtualFile): Boolean =
    file.getFileSystem.getProtocol != StandardFileSystems.JAR_PROTOCOL

  override def getBuilder = new ScFileStubBuilderImpl

  override def getExternalId = "scala.file"

  override final def serialize(stub: ScFileStub,
                               dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isScript)
  }

  override final def deserialize(dataStream: StubInputStream,
                                 parentStub: StubElement[_ <: PsiElement]): ScFileStubImpl =
    new ScFileStubImpl(null) {
      override val isScript: Boolean = dataStream.readBoolean()
    }

  override final def indexStub(stub: ScFileStub,
                               sink: IndexSink): Unit = {}

  import api.ScalaFile

  protected def instantiateFileStub(scalaFile: ScalaFile) = new ScFileStubImpl(scalaFile)

  protected class ScFileStubBuilderImpl extends DefaultStubBuilder {

    override def buildStubTree(file: PsiFile): ScFileStubImpl =
      super.buildStubTree(file).asInstanceOf[ScFileStubImpl]

    protected override def createStubForFile(file: PsiFile): ScFileStubImpl = file match {
      case ScStubFileElementType.ViewProvider(scalaFile: ScalaFile) => instantiateFileStub(scalaFile)
    }
  }

  protected class ScFileStubImpl(file: ScalaFile,
                                 override val getType: ScStubFileElementType = this)
    extends PsiFileStubImpl(file) with ScFileStub {

    override def isScript: Boolean = getPsi.isScriptFileImpl

    override final def getClasses: Array[PsiClass] = getChildrenByType(
      TokenSets.TYPE_DEFINITIONS,
      PsiClass.ARRAY_FACTORY
    )
  }

}

object ScStubFileElementType {

  object ViewProvider {

    def unapply(file: PsiFile): Option[PsiFile] =
      Option(file.getViewProvider.getPsi(ScalaLanguage.INSTANCE))

  }

}
