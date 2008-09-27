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
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackageContainerStub
import psi.stubs.elements.wrappers.DummyASTNode

/**
 * @author AlexanderPodkhalyuzin
* Date: 20.02.2008
 */

class ScPackagingImpl(node: ASTNode) extends ScalaStubBasedElementImpl[ScPackageContainer](node) with ScPackaging {
  def this(stub : ScPackageContainerStub) = {
    this(DummyASTNode)
    setStub(stub)
    setNode(null)
  }

  override def toString = "ScPackaging"

  private def reference = findChild(classOf[ScStableCodeReferenceElement])

  def getPackageName = reference match {
    case Some(r) => r.qualName
    case None => ""
  }

  private def innerRefName() = {
    def _innerRefName(ref : ScStableCodeReferenceElement) : String = ref.qualifier match {
      case Some(q) => _innerRefName(q)
      case None => ref.refName
    }
    reference match {case Some(r) => _innerRefName(r) case None => ""}
  }

  def ownNamePart = reference match {case Some(r) => r.qualName case None => ""}

  def prefix = {
    def parentPackageName(e : PsiElement): String = e.getParent match {
      case p: ScPackaging => {
        val _packName = parentPackageName(p)
        if (_packName.length > 0) _packName + "." + p.getPackageName else p.getPackageName
      }
      case f: ScalaFile => f.getPackageName
      case null => ""
      case parent => parentPackageName(parent)
    }
    parentPackageName(this)
  }

  override def packagings = findChildrenByClass(classOf[ScPackaging])

  def typeDefs = findChildrenByClass(classOf[ScTypeDefinition])

  def declaredElements = {
    val _prefix = prefix
    val top = if (_prefix.length > 0) _prefix + "." + innerRefName else innerRefName
    val p = JavaPsiFacade.getInstance(getProject).findPackage(top)
    if (p == null) Seq.empty else Seq.singleton(p)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val own = ownNamePart
    val i = own.indexOf(".")
    val top = if (i > 0) own.substring(0, i) else own

    var p = JavaPsiFacade.getInstance(getProject).findPackage(concat(prefix, top))
    p == null || p.processDeclarations(processor, state, lastParent, place)
  }
}