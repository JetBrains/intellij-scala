package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import java.util.ArrayList
import javax.swing.Icon

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPrimaryConstructorStub

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */

class ScPrimaryConstructorImpl private(stub: StubElement[ScPrimaryConstructor], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScPrimaryConstructor {
  def this(node: ASTNode) =
    this(null, null, node)

  def this(stub: ScPrimaryConstructorStub) =
    this(stub, ScalaElementTypes.PRIMARY_CONSTRUCTOR, null)

  override def getIcon(flags: Int): Icon = Icons.FUNCTION

  //todo rewrite me!
  override def hasModifier: Boolean = false

  def getClassNameText: String = {
    getNode.getTreeParent.getPsi.asInstanceOf[ScTypeDefinition].name
  }

  override def toString: String = "PrimaryConstructor"


  def parameterList: ScParameters = {
    getStubOrPsiChild(ScalaElementTypes.PARAM_CLAUSES)
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

  def getTypeParameters = PsiTypeParameter.EMPTY_ARRAY

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