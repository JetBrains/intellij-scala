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
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
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
      case ex: IOException => false
      case u: UnsupportedOperationException => false //why we need to handle this?
    }
  }

  private def canBeProcessed(file: VirtualFile, bytes: => Array[Byte]): Boolean = {
    val name: String = file.getNameWithoutExtension
    if (name.contains("$")) {
      val parent: VirtualFile = file.getParent
      @tailrec
      def checkName(name: String): Boolean = {
        val child: VirtualFile = parent.findChild(name + ".class")
        if (child != null) {
          val res = DecompilerUtil.isScalaFile(child)
          if (res) return true //let's handle it separately to avoid giving it for Java.
        }
        val index = name.lastIndexOf("$")
        if (index == -1) return false
        var newName = name.substring(0, index)
        while (newName.endsWith("$")) newName = newName.dropRight(1)
        checkName(newName)
      }
      checkName(name)
    } else DecompilerUtil.isScalaFile(file, bytes)
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
    val text = result.sourceText
    val file = ScalaPsiElementFactory.createScalaFile(text.replace("\r", ""),
      PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject))

    val adj = file.asInstanceOf[CompiledFileAdjuster]
    adj.setCompiled(c = true)
    adj.setSourceFileName(source)
    adj.setVirtualFile(vFile)

    val fType = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE).getFileNodeType
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
