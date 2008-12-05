package org.jetbrains.plugins.scala.lang.psi.impl.statements


import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, PhysicalSignature, ScFunctionType, ScSubstitutor}

import _root_.scala.collection.mutable.ArrayBuffer
import api.base.patterns.ScReferencePattern
import api.expr.{ScAnnotations, ScBlock}
import api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef.ScMember
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import stubs.elements.wrappers.DummyASTNode
import stubs.ScFunctionStub
import toplevel.synthetic.JavaIdentifier

import toplevel.typedef.{TypeDefinitionMembers, MixinNodes}
import impl.toplevel.typedef.TypeDefinitionMembers.MethodNodes
import java.util._
import com.intellij.lang._
import com.intellij.psi._
import com.intellij.psi.util._
import icons._
import lexer._
import types._
import api.base._
import api.base.types._
import api.statements._
import api.statements.params._
import api.toplevel.ScTypeParametersOwner

/**
 * @author ilyas
 */

abstract class ScFunctionImpl extends ScalaStubBasedElementImpl[ScFunction] with ScMember
    with ScFunction with ScTypeParametersOwner {

  def nameId(): PsiElement = {
    val n = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
      case null => getNode.findChildByType(ScalaTokenTypes.kTHIS)
      case n => n
    }
    if (n == null) {
      return ScalaPsiElementFactory.createIdentifier(getStub.asInstanceOf[ScFunctionStub].getName, getManager).getPsi
    }
    return n.getPsi
  }

  def parameters: Seq[ScParameter] = paramClauses.params

  override def getIcon(flags: Int) = Icons.FUNCTION

  def getReturnType = calcType match {
    case ScFunctionType(rt, _) => ScType.toPsi(rt, getProject, getResolveScope)
    case _ => PsiType.VOID
  }

  override def getPresentation(): ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText(): String = name
      def getTextAttributesKey(): TextAttributesKey = null
      def getLocationString(): String = "(" + ScFunctionImpl.this.getContainingClass.getQualifiedName + ")"
      override def getIcon(open: Boolean) = ScFunctionImpl.this.getIcon(0)
    }
  }

  def superMethods = TypeDefinitionMembers.getMethods(getContainingClass).
      get(new PhysicalSignature(this, ScSubstitutor.empty)) match {
    //partial match
    case Some(x) => x.supers.map{_.info.method}
  }

  def superMethod = TypeDefinitionMembers.getMethods(getContainingClass).
          get(new PhysicalSignature(this, ScSubstitutor.empty)) match {
    //partial match
    case Some(x) => x.primarySuper match {case Some (n) => Some(n.info.method) case None => None}
  }

  def superSignatures: Seq[FullSignature] = {
    val clazz = getContainingClass
    val s = new FullSignature(new PhysicalSignature(this, ScSubstitutor.empty), returnType, this, clazz)
    val t = TypeDefinitionMembers.getSignatures(clazz).get(s) match {
      //partial match
      case Some(x) => x.supers.map{_.info}
    }
    t
  }

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def getReturnTypeElement = null

  def getHierarchicalMethodSignature = null

  def findSuperMethods(parentClass: PsiClass) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods(checkAccess: Boolean) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods = PsiMethod.EMPTY_ARRAY

  def findDeepestSuperMethod = null

  def findDeepestSuperMethods = PsiMethod.EMPTY_ARRAY

  def getPom = null

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) =
    new ArrayList[MethodSignatureBackedByPsiMethod]()

  def getSignature(substitutor: PsiSubstitutor) = MethodSignatureBackedByPsiMethod.create(this, substitutor)

  def getTypeParameters = typeParameters.toArray

  //todo implement me!
  def isVarArgs = false

  def isConstructor = name == "this"

  def getBody = null

  def getThrowsList = findChildByClass(classOf[ScAnnotations])

  def getTypeParameterList = null

  def hasTypeParameters = false

  def getParameterList: ScParameters = paramClauses

  def calcType = paramClauses.clauses.toList.foldRight(returnType)((cl, t) =>
          ScFunctionType(t, cl.parameters.map{p => p.calcType}))

  override def getUseScope: SearchScope = {
    getParent match {
      case _: ScTemplateBody => super.getUseScope
      case _ => {
        var el: PsiElement = getParent
        while (el != null && !el.isInstanceOf[ScBlock] && !el.isInstanceOf[ScMember]) el = el.getParent
        if (el != null) new LocalSearchScope(el)
        else super.getUseScope
      }
    }
  }
}