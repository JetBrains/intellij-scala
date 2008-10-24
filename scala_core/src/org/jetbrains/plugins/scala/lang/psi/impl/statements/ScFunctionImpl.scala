package org.jetbrains.plugins.scala.lang.psi.impl.statements


import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, PhysicalSignature, ScFunctionType, ScSubstitutor}
import api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef.ScMember
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import stubs.elements.wrappers.DummyASTNode
import stubs.ScFunctionStub
import toplevel.synthetic.JavaIdentifier

import api.expr.ScAnnotations

import toplevel.typedef.{TypeDefinitionMembers, MixinNodes}
import impl.toplevel.typedef.TypeDefinitionMembers.MethodNodes
import java.util._
import com.intellij.lang._
import com.intellij.psi._
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.icons._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

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

  def superMethods: Seq[MethodNodes.Node] = if (getParent.isInstanceOf[ScTemplateBody]) TypeDefinitionMembers.
      getMethods(this.getContainingClass).
      get(new PhysicalSignature(this, ScSubstitutor.empty)) match {
    case None => Seq.empty
    case Some(x) => x.supers
  } else Seq.empty

  def superMethod: Option[MethodNodes.Node] = if (getParent.isInstanceOf[ScTemplateBody]) TypeDefinitionMembers.
      getMethods(this.getContainingClass).
      get(new PhysicalSignature(this, ScSubstitutor.empty)) match {
    case None => None
    case Some(x) => x.primarySuper
  } else None

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

  def isConstructor = false

  def getBody = null

  def getThrowsList = findChildByClass(classOf[ScAnnotations])

  def getTypeParameterList = null

  def hasTypeParameters = false

  def getParameterList: ScParameters = paramClauses

  def calcType = paramClauses.clauses.toList.foldRight(returnType) {((cl, t) =>
          new ScFunctionType(t, cl.parameters.map {p => p.calcType}))}
}