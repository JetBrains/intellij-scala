package org.jetbrains.plugins.scala.lang.psi.impl.compiled
import api.toplevel.packaging.{ScPackaging, ScPackageStatement}
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.impl.compiled.{ClsElementImpl, ClsRepositoryPsiElement}
import com.intellij.psi.impl.source.tree.{TreeElement, ElementType}
import com.intellij.psi.{PsiElement, PsiElementVisitor}

/**
 * @author ilyas
 */

class ScClsPackageStatementImpl extends ClsElementImpl with ScPackageStatement {

  val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.impl.compiled. ScClsPackageStatementImpl")

  var myFile: ScClsFileImpl = null
  var myPackageName: String = null

  def getPackageName: String = myPackageName

  def ownNamePart: String = getPackageName

  def prefix = ""

  def packagings: Seq[ScPackaging] = Seq.empty

  def appendMirrorText(indentLevel: Int, buffer: StringBuffer): Unit = {
    buffer.append("package ")
    buffer.append(getPackageName)
  }

  def this(file: ScClsFileImpl) = {
    this ()
    myFile = file;
    val classes = myFile.getClasses
    val clazzName: String = if (classes.length > 0) classes(0).getQualifiedName else ""
    val index = clazzName.lastIndexOf(".")
    if (index >= 0) {
      myPackageName = clazzName.substring(0, index)
    }
  }

  def setMirror(element: TreeElement): Unit = {
    myMirror = element
  }

  def getChildren: Array[PsiElement] = {
    LOG.error("method not implemented")
    null
  }

  def accept(visitor: PsiElementVisitor): Unit = {
    visitor.visitElement(this)
  }

  override def toString: String = "ScPackageStatement: " + getPackageName

  protected def findChildByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = null

  protected def findChildrenByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = Array[T]()

  def typeDefs: Seq[ScTypeDefinition] = myFile.getTypeDefinitions

  def getParent: PsiElement = myFile

}