package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements


import api.expr.{ScAnnotations, ScBlock}
import api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef.ScMember
import api.toplevel.{ScTypeParametersOwner}
import caches.CachesUtil
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import parser.ScalaElementTypes
import stubs.ScFunctionStub
import toplevel.typedef.{TypeDefinitionMembers}
import java.util._
import com.intellij.psi._
import com.intellij.psi.util._
import icons._
import impl.source.HierarchicalMethodSignatureImpl
import lexer._
import tree.TokenSet
import types._
import api.statements._
import api.statements.params._
import result.{TypeResult, Success, TypingContext}
import com.intellij.openapi.project.DumbServiceImpl
import toplevel.synthetic.{SyntheticClasses, JavaIdentifier}

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

  def getReturnType: PsiType = {
    if (DumbServiceImpl.getInstance(getProject).isDumb || !SyntheticClasses.get(getProject).isClassesRegistered) {
      return null //no resolve during dumb mode or while synthetic classes is not registered
    }
    CachesUtil.get(
      this, CachesUtil.PSI_RETURN_TYPE_KEY,
      new CachesUtil.MyProvider(this, {ic: ScFunctionImpl => ic.getReturnTypeImpl})
        (PsiModificationTracker.MODIFICATION_COUNT)
      )
  }

  private def getReturnTypeImpl: PsiType = {
    getType(TypingContext.empty).getOrElse(Any) match {
      case ScFunctionType(rt, _) => ScType.toPsi(rt, getProject, getResolveScope)
      case x => ScType.toPsi(x, getProject, getResolveScope)
    }
  }

  def superMethods: Seq[PsiMethod] = {
    val clazz = getContainingClass
    if (clazz != null) TypeDefinitionMembers.getMethods(clazz).
          get(new PhysicalSignature(this, ScSubstitutor.empty)).getOrElse(return Seq.empty).supers.map {_.info.method}
    else Seq.empty
  }

  def superMethod: Option[PsiMethod] = superMethodAndSubstitutor.map(_._1)

  def superMethodAndSubstitutor: Option[(PsiMethod, ScSubstitutor)] = {
    val clazz = getContainingClass
    if (clazz != null) {
      val option = TypeDefinitionMembers.getMethods(clazz).
          smartGet(new PhysicalSignature(this, ScSubstitutor.empty))
      if (option == None) return None
      option.get.primarySuper.map(node => (node.info.method, node.info.substitutor))
    }
    else None
  }


  def superSignatures: Seq[FullSignature] = {
    val clazz = getContainingClass
    val s = new FullSignature(new PhysicalSignature(this, ScSubstitutor.empty), new Suspension(() =>
      returnType.getOrElse(Any)), this, Some(clazz))
    if (clazz == null) return Seq(s)
    val t = TypeDefinitionMembers.getSignatures(clazz).get(s) match {
    //partial match
      case Some(x) => x.supers.map {_.info}
      case None => Seq[FullSignature]()
    }
    t
  }

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def findDeepestSuperMethod: PsiMethod = {
    val s = superMethods
    if (s.length == 0) return null
    else s(s.length - 1)
  }

  def getReturnTypeElement = null

  def findSuperMethods(parentClass: PsiClass) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods(checkAccess: Boolean) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods = superMethods.toArray // TODO which other xxxSuperMethods can/should be implemented?

  def findDeepestSuperMethods = PsiMethod.EMPTY_ARRAY

  def getMethodReceiver: PsiMethodReceiver = null

  def getReturnTypeNoResolve: PsiType = PsiType.VOID

  def getPom = null

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) =
    new ArrayList[MethodSignatureBackedByPsiMethod]()

  def getSignature(substitutor: PsiSubstitutor) = MethodSignatureBackedByPsiMethod.create(this, substitutor)

  def getTypeParameters = typeParameters.toArray

  //todo implement me!
  def isVarArgs = false

  def isConstructor = name == "this"

  def getBody: PsiCodeBlock = null

  def getThrowsList = findChildByClass(classOf[ScAnnotations])

  def getTypeParameterList = null

  def hasTypeParameters = typeParameters.length > 0

  def getParameterList: ScParameters = paramClauses

  def getType(ctx: TypingContext) = {
    returnType match {
      case Success(tp: ScType, _) => {
        var res: TypeResult[ScType] = Success(tp, None)
        var i = paramClauses.clauses.length - 1
        while (i >= 0) {
          val cl = paramClauses.clauses.apply(i)
          val paramTypes = cl.parameters.map(_.getType(ctx))
          res match {
            case Success(t: ScType, _) => {
              res = collectFailures(paramTypes, Nothing)(new ScFunctionType(t, _)(getProject, getResolveScope))
            }
            case _ =>
          }
          i = i - 1
        }
        res
      }
      case x => x
    }
  }

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

  override protected def isSimilarMemberForNavigation(m: ScMember, strictCheck: Boolean) = m match {
    case f: ScFunction => f.name == name && {
      if (strictCheck) new PhysicalSignature(this, ScSubstitutor.empty).paramTypesEquiv(new PhysicalSignature(f, ScSubstitutor.empty))
      else true
    }
    case _ => false
  }


  def paramClauses: ScParameters = {
    getStubOrPsiChild(ScalaElementTypes.PARAM_CLAUSES)
  }

  def hasAssign = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tASSIGN)).size > 0

  def getHierarchicalMethodSignature: HierarchicalMethodSignature = {
    new HierarchicalMethodSignatureImpl(getSignature(PsiSubstitutor.EMPTY))
  }

  override def isDeprecated = {
    hasAnnotation("scala.deprecated") != None || hasAnnotation("java.lang.Deprecated") != None
  }

  override def getName = if (isConstructor && getContainingClass != null) getContainingClass.getName else super.getName

  override def setName(name: String): PsiElement = {
    if (isConstructor) return this
    else super.setName(name)
  }
}