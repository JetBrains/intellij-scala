package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.psi.stubs.{PsiFileStub, PsiFileStubImpl}
import com.intellij.util.indexing.FileContent
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory}

object ScClsStubBuilder extends ClsStubBuilder {

  import DecompilerUtil._

  override def getStubVersion: Int = DECOMPILER_VERSION

  override def buildFileStub(content: FileContent): PsiFileStub[ScalaFile] =
    content.getFile match {
      case file if isInnerClass(file) => null
      case file =>
        val scalaFile = createScalaFile(file, content.getContent)(content.getProject)

        val result = scalaBuilder.buildStubTree(scalaFile)
          .asInstanceOf[PsiFileStubImpl[ScalaFile]]
        result.clearPsi("Stub was built from decompiled file")
        result
    }

  private def createScalaFile(file: VirtualFile, content: Array[Byte])
                             (implicit project: Project) = {
    val sourceText = decompile(file, content).sourceText

    val result = ScalaPsiElementFactory.createScalaFileFromText(sourceText)
      .asInstanceOf[ScalaFileImpl]
    result.isCompiled = true
    result.virtualFile = file

    result
  }

  private def scalaBuilder = {
    val parserDefinition = LanguageParserDefinitions.INSTANCE
      .forLanguage(ScalaLanguage.INSTANCE)
      .asInstanceOf[ScalaParserDefinition]

    parserDefinition.getFileNodeType.getBuilder
  }

  private def isInnerClass(file: VirtualFile): Boolean =
    file.getParent match {
      case null => false
      case parent => !file.isClass && parent.isInner(file.getNameWithoutExtension)
    }

  private implicit class VirtualFileExt(private val virtualFile: VirtualFile) extends AnyVal {

    import reflect.NameTransformer.decode

    def isInner(name: String): Boolean = {
      if (name.endsWith("$") && contains(name, name.length - 1)) {
        return false //let's handle it separately to avoid giving it for Java.
      }
      isInner(decode(name), 0)
    }

    private def isInner(name: String, from: Int): Boolean = {
      val index = name.indexOf('$', from)

      val containsPart = index > 0 && contains(name, index)
      index != -1 && (containsPart || isInner(name, index + 1))
    }

    private def contains(name: String, endIndex: Int): Boolean =
      virtualFile.getChildren.exists { child =>
        child.isClass &&
          decode(child.getNameWithoutExtension) != name.substring(0, endIndex)
      }

    def isClass: Boolean = virtualFile.getExtension == "class"
  }

}
