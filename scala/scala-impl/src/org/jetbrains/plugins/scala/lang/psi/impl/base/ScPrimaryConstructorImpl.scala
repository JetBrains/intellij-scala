package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPrimaryConstructorStub

import java.util
import javax.swing.Icon

class ScPrimaryConstructorImpl private(stub: ScPrimaryConstructorStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.PRIMARY_CONSTRUCTOR, node) with ScPrimaryConstructor {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScPrimaryConstructorStub) = this(stub, null)

  override def getIcon(flags: Int): Icon = Icons.FUNCTION

  //todo rewrite me!
  override def hasModifier: Boolean = false

  override def getClassNameText: String = {
    getNode.getTreeParent.getPsi.asInstanceOf[ScTypeDefinition].name
  }

  override def toString: String = "PrimaryConstructor"

  override def parameterList: ScParameters = {
    getStubOrPsiChild(ScalaElementType.PARAM_CLAUSES)
  }

  override def getName: String = this.containingClass.name

  override def getNameIdentifier: PsiIdentifier = null

  override def getReturnTypeElement: PsiTypeElement = null

  override def getHierarchicalMethodSignature = new HierarchicalMethodSignatureImpl(getSignature(PsiSubstitutor.EMPTY))

  override def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findDeepestSuperMethod: PsiMethod = null

  override def findDeepestSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) =
    new util.ArrayList[MethodSignatureBackedByPsiMethod]()

  override def getSignature(substitutor: PsiSubstitutor): MethodSignatureBackedByPsiMethod = MethodSignatureBackedByPsiMethod.create(this, substitutor)

  override def psiTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  //todo implement me!
  override def isVarArgs = false

  override def isConstructor = true

  override def getBody: PsiCodeBlock = null

  override def getThrowsList: ScAnnotations = findChildByClass(classOf[ScAnnotations])

  override def getTypeParameterList: PsiTypeParameterList = null

  override def hasTypeParameters: Boolean = false

  override def getParameterList: ScParameters = parameterList

  override def setName(name: String): PsiElement = this

  override def getReturnType: PsiType = PsiType.VOID

  override def getDocComment: PsiDocComment = null

  override def isDeprecated: Boolean = false
}