package org.jetbrains.plugins.scala
package decompiler

import java.io.IOException

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.psi.stubs.{PsiFileStub, PsiFileStubImpl}
import com.intellij.util.indexing.FileContent
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil.decompile
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StubVersion
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt}

import scala.annotation.tailrec
import scala.reflect.NameTransformer

/**
  * @author ilyas
  */
object ScClsStubBuilder {
  def canBeProcessed(file: VirtualFile): Boolean = {
    try {
      canBeProcessed(file, file.contentsToByteArray())
    } catch {
      case _: IOException => false
      case _: UnsupportedOperationException => false //why we need to handle this?
    }
  }

  private def canBeProcessed(file: VirtualFile, bytes: => Array[Byte]): Boolean = {
    if (DecompilerUtil.isScalaFile(file, bytes)) return true
    val fileName: String = file.getNameWithoutExtension
    val parent = file.getParent

    def split(str: String): Option[(String, String)] = {
      val index = str.indexOf('$')
      if (index == -1) None
      else Some(str.substring(0, index), str.substring(index + 1, str.length))
    }

    @tailrec
    def go(prefix: String, suffix: String): Boolean = {
      if (!prefix.endsWith("$")) {
        val isScala =
          Option(parent)
            .map(_.findChild(prefix + ".class"))
            .exists(DecompilerUtil.isScalaFile)

        if (isScala) return true
      }
      split(suffix) match {
        case Some((suffixPrefix, suffixSuffix)) => go(prefix + "$" + suffixPrefix, suffixSuffix)
        case _ => false
      }
    }

    split(fileName) match {
      case Some((prefix, suffix)) => go(prefix, suffix)
      case _ => false
    }
  }

  private class Directory(directory: VirtualFile) {

    def isInner(name: String): Boolean = {
      if (name.endsWith("$") && contains(name, name.length - 1)) {
        return false //let's handle it separately to avoid giving it for Java.
      }
      isInner(NameTransformer.decode(name), 0)
    }

    private def isInner(name: String, from: Int): Boolean = {
      val index = name.indexOf('$', from)

      val containsPart = index > 0 && contains(name, index)
      index != -1 && (containsPart || isInner(name, index + 1))
    }

    private def contains(name: String, endIndex: Int): Boolean =
      directory.getChildren.exists { child =>
        child.getExtension == "class" &&
          NameTransformer.decode(child.getNameWithoutExtension) != name.substring(0, endIndex)
      }
  }

}

class ScClsStubBuilder extends ClsStubBuilder {
  override def getStubVersion: Int = StubVersion.STUB_VERSION

  override def buildFileStub(content: FileContent): PsiFileStub[ScalaFile] =
    content.getFile match {
      case file if isInnerClass(file) => null
      case file =>
        val scalaFile = createScalaFile(file, content.getContent)(content.getProject)
        createFileStub(scalaFile)
    }

  private def createFileStub(file: ScalaFile): PsiFileStub[ScalaFile] = {
    val language = file.getProject.language

    val fileElementType = LanguageParserDefinitions.INSTANCE.forLanguage(language) match {
      case definition: ScalaParserDefinition => definition.getFileNodeType
    }

    val result = fileElementType.getBuilder.buildStubTree(file)
      .asInstanceOf[PsiFileStubImpl[ScalaFile]]
    result.clearPsi("Stub was built from decompiled file")
    result
  }

  private def createScalaFile(virtualFile: VirtualFile, bytes: Array[Byte])
                             (implicit ctx: ProjectContext): ScalaFile = {
    val decompiled = decompile(virtualFile, bytes)
    val result = createScalaFileFromText(decompiled.sourceText.replace("\r", ""))

    val adjuster = result.asInstanceOf[CompiledFileAdjuster]
    adjuster.setCompiled(c = true)
    adjuster.setSourceFileName(decompiled.sourceName)
    adjuster.setVirtualFile(virtualFile)

    result
  }

  private def isInnerClass(file: VirtualFile): Boolean =
    file.getExtension match {
      case "class" => false
      case _ =>
        import ScClsStubBuilder.Directory
        Option(file.getParent).map {
          new Directory(_)
        }.exists {
          _.isInner(file.getNameWithoutExtension)
        }
    }
}
