package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging

import api.base.ScStableCodeReferenceElement
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.stubs.IStubElementType
import parser.ScalaElementTypes
import psi.ScalaPsiElementImpl
import api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.ASTNodeWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackageContainerStub

/**
 * @author AlexanderPodkhalyuzin
* Date: 20.02.2008
 */

class ScPackagingImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPackaging {

  override def toString = "ScPackaging"

  private def reference = findChild(classOf[ScStableCodeReferenceElement])

  // One more hack for correct inheritance
  override def getElementType: IStubElementType[Nothing, Nothing] =
    super.getElementType.asInstanceOf[IStubElementType[Nothing, Nothing]];

  def getPackageName = reference match {
    case Some(r) => r.qualName
    case None => ""
  }

  def parentPackageName(e : PsiElement): String = e.getParent match {
    case p: ScPackaging => {
      val _packName = parentPackageName(p)
      if (_packName.length > 0) _packName + "." + p.getPackageName else p.getPackageName
    }
    case f: ScalaFile => f.getPackageName
    case null => ""
    case parent => parentPackageName(parent)
  }

  def fqn = {
    def innerRefName(ref : ScStableCodeReferenceElement) : String = ref.qualifier match {
      case Some(q) => innerRefName(q)
      case None => ref.refName
    }
    val pname = parentPackageName(this)
    val refName = reference match {case Some(r) => innerRefName(r) case None => ""}
    if(pname.length > 0) pname + "." + refName else refName
  }

  override def packagings = findChildrenByClass(classOf[ScPackaging])

  def typeDefs = findChildrenByClass(classOf[ScTypeDefinition])

  def declaredElements = {
    val p = JavaPsiFacade.getInstance(getProject).findPackage(fqn)
    assert (p != null)
    Seq.singleton(p)
  }

}

object ScPackagingImpl {
  def apply(stub: ScPackageContainerStub): ScPackaging = new ScPackagingImpl(new ASTNodeWrapper()) {
    setNode(null)
    setStubImpl(stub)
    override def getElementType = ScalaElementTypes.PACKAGING.asInstanceOf[IStubElementType[Nothing, Nothing]]
  }
}
