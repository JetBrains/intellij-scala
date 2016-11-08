package org.jetbrains.plugins.scala
package decompiler

import java.io.IOException

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.project.{DefaultProjectFactory, Project, ProjectManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.psi.stubs.{PsiFileStub, PsiFileStubImpl}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.indexing.FileContent
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StubVersion

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
        val child = parent.findChild(prefix + ".class")
        if (child != null && DecompilerUtil.isScalaFile(child)) return true
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
}

class ScClsStubBuilder extends ClsStubBuilder {
  override def getStubVersion: Int = StubVersion.STUB_VERSION

  override def buildFileStub(content: FileContent): PsiFileStub[ScalaFile] = {
    if (isInnerClass(content.getFile)) null
    else buildFileStub(content.getFile, content.getContent, ProjectManager.getInstance().getDefaultProject)
  }

  private def buildFileStub(vFile: VirtualFile, bytes: Array[Byte], project: Project): PsiFileStub[ScalaFile] = {
    val result = DecompilerUtil.decompile(vFile, bytes)
    val source = result.sourceName

    val text = result.sourceText.replace("\r", "")
    implicit val manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject)
    val file = createScalaFileFromText(text)(manager)

    val adj = file.asInstanceOf[CompiledFileAdjuster]
    adj.setCompiled(c = true)
    adj.setSourceFileName(source)
    adj.setVirtualFile(vFile)

    val fType = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaLanguage.INSTANCE).getFileNodeType
    val stub = fType.asInstanceOf[IStubFileElementType[PsiFileStub[PsiFile]]].getBuilder.buildStubTree(file)
    stub.asInstanceOf[PsiFileStubImpl[PsiFile]].clearPsi("Stub was built from decompiled file")
    stub.asInstanceOf[PsiFileStub[ScalaFile]]
  }

  private def isInnerClass(file: VirtualFile): Boolean = {
    if (file.getExtension != "class") return false
    val name: String = file.getNameWithoutExtension
    val parent: VirtualFile = file.getParent
    isInner(name, new ParentDirectory(parent))
  }

  private def isInner(name: String, directory: Directory): Boolean = {
    if (name.endsWith("$") && directory.contains(name.dropRight(1))) {
      return false //let's handle it separately to avoid giving it for Java.
    }
    isInner(NameTransformer.decode(name), 0, directory)
  }

  @tailrec
  private def isInner(name: String, from: Int, directory: Directory): Boolean = {
    val index: Int = name.indexOf('$', from)
    index != -1 && (containsPart(directory, name, index) || isInner(name, index + 1, directory))
  }

  private def containsPart(directory: Directory, name: String, endIndex: Int): Boolean = {
    endIndex > 0 && directory.contains(name.substring(0, endIndex))
  }

  private trait Directory {
    def contains(name: String): Boolean
  }

  private class ParentDirectory(dir: VirtualFile) extends Directory {
    def contains(name: String): Boolean = {
      if (dir == null) return false
      !dir.getChildren.forall(child =>
        child.getExtension != "class" || NameTransformer.decode(child.getNameWithoutExtension) == name
      )
    }
  }
}
