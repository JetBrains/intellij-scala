package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import java.util.ArrayList

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotations, _}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPrimaryConstructorStub

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */

class ScPrimaryConstructorImpl private(stub: ScPrimaryConstructorStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.PRIMARY_CONSTRUCTOR, node) with ScPrimaryConstructor {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScPrimaryConstructorStub) = this(stub, null)

  override def getIcon(flags: Int): Icon = Icons.FUNCTION

  //todo rewrite me!
  override def hasModifier: Boolean = false

  def getClassNameText: String = {
    getNode.getTreeParent.getPsi.asInstanceOf[ScTypeDefinition].name
  }

  override def toString: String = "PrimaryConstructor"


  def parameterList: ScParameters = {
    getStubOrPsiChild(ScalaElementType.PARAM_CLAUSES)
  }

  override def getName: String = this.containingClass.name

  def getNameIdentifier: PsiIdentifier = null

  def getReturnTypeElement = null

  def getHierarchicalMethodSignature = new HierarchicalMethodSignatureImpl(getSignature(PsiSubstitutor.EMPTY))

  def findSuperMethods(parentClass: PsiClass) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods(checkAccess: Boolean) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods = PsiMethod.EMPTY_ARRAY

  def findDeepestSuperMethod = null

  def findDeepestSuperMethods = PsiMethod.EMPTY_ARRAY

  def getReturnTypeNoResolve: PsiType = PsiType.VOID

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) =
    new ArrayList[MethodSignatureBackedByPsiMethod]()

  def getSignature(substitutor: PsiSubstitutor): MethodSignatureBackedByPsiMethod = MethodSignatureBackedByPsiMethod.create(this, substitutor)

  def psiTypeParameters = PsiTypeParameter.EMPTY_ARRAY

  //todo implement me!
  def isVarArgs = false

  def isConstructor = true

  def getBody = null

  def getThrowsList: ScAnnotations = findChildByClass(classOf[ScAnnotations])

  def getTypeParameterList = null

  def hasTypeParameters = false

  def getParameterList: ScParameters = parameterList

  def setName(name: String): PsiElement = this

  def getReturnType: PsiType = PsiType.VOID

  def getDocComment: PsiDocComment = null

  def isDeprecated: Boolean = false
}