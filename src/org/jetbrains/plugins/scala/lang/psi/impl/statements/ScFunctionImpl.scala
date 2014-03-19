package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements


import api.expr.{ScAnnotations, ScBlock}
import api.toplevel.templates.ScTemplateBody
import api.toplevel.ScTypeParametersOwner
import caches.CachesUtil
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import parser.ScalaElementTypes
import stubs.ScFunctionStub
import toplevel.typedef.TypeDefinitionMembers
import java.util._
import com.intellij.psi._
import com.intellij.psi.util._
import icons._
import impl.source.HierarchicalMethodSignatureImpl
import lexer._
import scope.PsiScopeProcessor
import tree.TokenSet
import types._
import api.statements._
import api.statements.params._
import result.{TypeResult, Success, TypingContext}
import com.intellij.openapi.project.DumbServiceImpl
import toplevel.synthetic.{SyntheticClasses, JavaIdentifier}
import fake.{FakePsiReferenceList, FakePsiTypeParameterList}
import collection.mutable.ArrayBuffer
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiReferenceList.Role
import extensions.toPsiClassExt
import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.pom.java.LanguageLevel

/**
 * @author ilyas
 */

abstract class ScFunctionImpl extends ScalaStubBasedElementImpl[ScFunction] with ScMember
        with ScFunction with ScTypeParametersOwner {
  override def isStable = false

  def nameId: PsiElement = {
    val n = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
      case null => getNode.findChildByType(ScalaTokenTypes.kTHIS)
      case n => n
    }
    if (n == null) {
      return ScalaPsiElementFactory.createIdentifier(getStub.asInstanceOf[ScFunctionStub].getName, getManager).getPsi
    }
    n.getPsi
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
    val tp = getType(TypingContext.empty).getOrAny
    tp match {
      case ScFunctionType(rt, _) => ScType.toPsi(rt, getProject, getResolveScope)
      case _ => ScType.toPsi(tp, getProject, getResolveScope)
    }
  }

  def superMethods: Seq[PsiMethod] = {
    val clazz = containingClass
    if (clazz != null) TypeDefinitionMembers.getSignatures(clazz).forName(ScalaPsiUtil.convertMemberName(name))._1.
          get(new PhysicalSignature(this, ScSubstitutor.empty)).getOrElse(return Seq.empty).supers.
      filter(_.info.isInstanceOf[PhysicalSignature]).map {_.info.asInstanceOf[PhysicalSignature].method}
    else Seq.empty
  }

  def superMethod: Option[PsiMethod] = superMethodAndSubstitutor.map(_._1)

  def superMethodAndSubstitutor: Option[(PsiMethod, ScSubstitutor)] = {
    val clazz = containingClass
    if (clazz != null) {
      val option = TypeDefinitionMembers.getSignatures(clazz).forName(name)._1.
          fastPhysicalSignatureGet(new PhysicalSignature(this, ScSubstitutor.empty))
      if (option == None) return None
      option.get.primarySuper.filter(_.info.isInstanceOf[PhysicalSignature]).
        map(node => (node.info.asInstanceOf[PhysicalSignature].method, node.info.substitutor))
    }
    else None
  }


  def superSignatures: Seq[Signature] = {
    val clazz = containingClass
    val s = new PhysicalSignature(this, ScSubstitutor.empty)
    if (clazz == null) return Seq(s)
    val t = TypeDefinitionMembers.getSignatures(clazz).forName(ScalaPsiUtil.convertMemberName(name))._1.
      fastPhysicalSignatureGet(s) match {
      case Some(x) => x.supers.map {_.info}
      case None => Seq[Signature]()
    }
    t
  }

  def superSignaturesIncludingSelfType: Seq[Signature] = {
    val clazz = containingClass
    val s = new PhysicalSignature(this, ScSubstitutor.empty)
    if (clazz == null) return Seq(s)
    val t = TypeDefinitionMembers.getSelfTypeSignatures(clazz).forName(ScalaPsiUtil.convertMemberName(name))._1.
      fastPhysicalSignatureGet(s) match {
      case Some(x) => x.supers.map {_.info}
      case None => Seq[Signature]()
    }
    t
  }


  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def findDeepestSuperMethod: PsiMethod = {
    val s = superMethods
    if (s.length == 0) null
    else s(s.length - 1)
  }

  def getReturnTypeElement = null

  def findSuperMethods(parentClass: PsiClass) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods(checkAccess: Boolean) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods = superMethods.toArray // TODO which other xxxSuperMethods can/should be implemented?

  def findDeepestSuperMethods = PsiMethod.EMPTY_ARRAY

  def getReturnTypeNoResolve: PsiType = PsiType.VOID

  def getPom = null

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) =
    new ArrayList[MethodSignatureBackedByPsiMethod]()

  def getSignature(substitutor: PsiSubstitutor) = MethodSignatureBackedByPsiMethod.create(this, substitutor)

  def getTypeParameters: Array[PsiTypeParameter] = {
    val params = typeParameters
    val size = params.length
    val result = PsiTypeParameter.ARRAY_FACTORY.create(size)
    var i = 0
    while (i < size) {
      result(i) = params(i).asInstanceOf[PsiTypeParameter]
      i += 1
    }
    result
  }

  //todo implement me!
  def isVarArgs = false

  def isConstructor = name == "this"

  def getBody: PsiCodeBlock = null

  def getThrowsList = new FakePsiReferenceList(getManager, getLanguage, Role.THROWS_LIST) {
    override def getReferenceElements: Array[PsiJavaCodeReferenceElement] = {
      getReferencedTypes.map {
        tp => PsiElementFactory.SERVICE.getInstance(getProject).createReferenceElementByType(tp)
      }
    }

    override def getReferencedTypes: Array[PsiClassType] = {
      hasAnnotation("scala.throws") match {
        case Some(annotation) =>
          annotation.constructor.args.map(_.exprs).getOrElse(Seq.empty).flatMap { expr =>
            expr.getType(TypingContext.empty) match {
              case Success(ScParameterizedType(des, Seq(arg)), _) => ScType.extractClass(des) match {
                case Some(clazz) if clazz.qualifiedName == "java.lang.Class" =>
                  ScType.toPsi(arg, getProject, getResolveScope) match {
                    case c: PsiClassType => Seq(c)
                    case _ => Seq.empty
                  }
                case _ => Seq.empty
              }
              case _ => Seq.empty
            }
          }.toArray
        case _ => PsiClassType.EMPTY_ARRAY
      }
    }
  }

  def getTypeParameterList = new FakePsiTypeParameterList(getManager, getLanguage, typeParameters.toArray, this)

  def hasTypeParameters = typeParameters.length > 0

  def getParameterList: ScParameters = paramClauses

  def getType(ctx: TypingContext) = {
    returnType match {
      case Success(tp: ScType, _) =>
        var res: TypeResult[ScType] = Success(tp, None)
        var i = paramClauses.clauses.length - 1
        while (i >= 0) {
          val cl = paramClauses.clauses.apply(i)
          val paramTypes = cl.parameters.map(_.getType(ctx))
          res match {
            case Success(t: ScType, _) =>
              res = collectFailures(paramTypes, Nothing)(ScFunctionType(t, _)(getProject, getResolveScope))
            case _ =>
          }
          i = i - 1
        }
        res
      case x => x
    }
  }

  override protected def isSimilarMemberForNavigation(m: ScMember, strictCheck: Boolean) = m match {
    case f: ScFunction => f.name == name && {
      if (strictCheck) new PhysicalSignature(this, ScSubstitutor.empty).
        paramTypesEquiv(new PhysicalSignature(f, ScSubstitutor.empty))
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

  override def getName = {
    val res = if (isConstructor && getContainingClass != null) getContainingClass.getName else super.getName
    if (JavaLexer.isKeyword(res, LanguageLevel.HIGHEST)) "_mth" + res
    else res
  }

  override def setName(name: String): PsiElement = {
    if (isConstructor) this
    else super.setName(name)
  }


  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    // process function's process type parameters
    if (!super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)) return false

    lazy val parameterIncludingSynthetic: Seq[ScParameter] = effectiveParameterClauses.flatMap(_.parameters)
    if (getStub == null) {
      returnTypeElement match {
        case Some(x) if lastParent != null && x.getStartOffsetInParent == lastParent.getStartOffsetInParent =>
          for (p <- parameterIncludingSynthetic) {
            ProgressManager.checkCanceled()
            if (!processor.execute(p, state)) return false
          }
        case _ =>
      }
    } else {
      if (lastParent != null && lastParent.getContext != lastParent.getParent) {
        for (p <- parameterIncludingSynthetic) {
          ProgressManager.checkCanceled()
          if (!processor.execute(p, state)) return false
        }
      }
    }
    true
  }

  override def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq originalClass) return this
    if (!originalClass.isInstanceOf[ScTypeDefinition]) return this
    val c = originalClass.asInstanceOf[ScTypeDefinition]
    val membersIterator = c.members.iterator
    val buf: ArrayBuffer[ScMember] = new ArrayBuffer[ScMember]
    while (membersIterator.hasNext) {
      val member = membersIterator.next()
      if (isSimilarMemberForNavigation(member, false)) buf += member
    }
    if (buf.length == 0) this
    else if (buf.length == 1) buf(0)
    else {
      val filter = buf.filter(isSimilarMemberForNavigation(_, true))
      if (filter.length == 0) buf(0)
      else filter(0)
    }
  }

  def getTypeNoImplicits(ctx: TypingContext): TypeResult[ScType] = {
    returnType match {
      case Success(tp: ScType, _) =>
        var res: TypeResult[ScType] = Success(tp, None)
        var i = paramClauses.clauses.length - 1
        while (i >= 0) {
          val cl = paramClauses.clauses.apply(i)
          if (!cl.isImplicit) {
            val paramTypes = cl.parameters.map(_.getType(ctx))
            res match {
              case Success(t: ScType, _) =>
                res = collectFailures(paramTypes, Nothing)(ScFunctionType(t, _)(getProject, getResolveScope))
              case _ =>
            }
          }
          i = i - 1
        }
        res
      case failure => failure
    }
  }
}