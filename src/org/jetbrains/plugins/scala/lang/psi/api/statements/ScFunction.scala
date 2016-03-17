package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements


import java.util

import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiReferenceList.Role
import com.intellij.psi._
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScModifiableTypedDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.fake.{FakePsiReferenceList, FakePsiTypeParameterList}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{JavaIdentifier, ScSyntheticFunction, ScSyntheticTypeParameter, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.light.scala.{ScLightFunctionDeclaration, ScLightFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{Unit => UnitType, _}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInsidePsiElement, ModCount}

import scala.annotation.tailrec
import scala.collection.Seq
import scala.collection.immutable.Set
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun extends ScTypeParametersOwner {
  def retType: ScType

  def paramClauses: Seq[Seq[Parameter]]

  def methodType: ScType = {
    paramClauses.foldRight[ScType](retType) {
      (params: Seq[Parameter], tp: ScType) => new ScMethodType(tp, params, false)(getProject, getResolveScope)
    }
  }

  def polymorphicType: ScType = {
    if (typeParameters.isEmpty) methodType
    else ScTypePolymorphicType(methodType, typeParameters.map(new TypeParameter(_)))
  }
}


/**
 * Represents Scala's internal function definitions and declarations
 */
trait ScFunction extends ScalaPsiElement with ScMember with ScTypeParametersOwner
  with ScParameterOwner with ScDocCommentOwner with ScTypedDefinition with ScCommentOwner
  with ScDeclaredElementsHolder with ScAnnotationsHolder with ScMethodLike with ScBlockStatement
  with ScModifiableTypedDeclaration {

  private var synthNavElement: Option[PsiElement] = None
  var syntheticCaseClass: Option[ScClass] = None
  var syntheticContainingClass: Option[ScTypeDefinition] = None
  def setSynthetic(navElement: PsiElement) {
    synthNavElement = Some(navElement)
  }
  def isSyntheticCopy: Boolean = synthNavElement.nonEmpty && name == "copy"
  def isSyntheticApply: Boolean = synthNavElement.nonEmpty && name == "apply"
  def isSyntheticUnapply: Boolean = synthNavElement.nonEmpty && name == "unapply"
  def isSyntheticUnapplySeq: Boolean = synthNavElement.nonEmpty && name == "unapplySeq"
  def isSynthetic: Boolean = synthNavElement.nonEmpty
  def getSyntheticNavigationElement: Option[PsiElement] = synthNavElement

  def hasUnitResultType = {
    @tailrec
    def hasUnitRT(t: ScType): Boolean = t match {
      case UnitType => true
      case ScMethodType(result, _, _) => hasUnitRT(result)
      case _ => false
    }
    hasUnitRT(methodType)
  }

  def isParameterless = paramClauses.clauses.isEmpty

  private val probablyRecursive: ThreadLocal[Boolean] = new ThreadLocal[Boolean]() {
    override def initialValue(): Boolean = false
  }
  def isProbablyRecursive = probablyRecursive.get()
  def setProbablyRecursive(b: Boolean) {probablyRecursive.set(b)}

  def isEmptyParen = paramClauses.clauses.size == 1 && paramClauses.params.isEmpty

  def addEmptyParens() {
    val clause = ScalaPsiElementFactory.createClauseFromText("()", getManager)
    paramClauses.addClause(clause)
  }

  def removeAllClauses() {
    paramClauses.clauses.headOption.zip(paramClauses.clauses.lastOption).foreach { p =>
      paramClauses.deleteChildRange(p._1, p._2)
    }
  }

  def isNative: Boolean = {
    hasAnnotation("scala.native").isDefined
  }

  override def hasModifierProperty(name: String): Boolean = {
    if (name == "abstract") {
      this match {
        case _: ScFunctionDeclaration =>
          containingClass match {
            case t: ScTrait => return true
            case c: ScClass if c.hasAbstractModifier => return true
            case _ =>
          }
        case _ =>
      }
    }
    super.hasModifierProperty(name)
  }

  /**
   * This method is important for expected type evaluation.
   */
  def getInheritedReturnType: Option[ScType] = {
    returnTypeElement match {
      case Some(_) => returnType.toOption
      case None =>
        val superReturnType = superMethodAndSubstitutor match {
          case Some((fun: ScFunction, subst)) =>
            var typeParamSubst = ScSubstitutor.empty
            fun.typeParameters.zip(typeParameters).foreach {
              case (oldParam: ScTypeParam, newParam: ScTypeParam) =>
                typeParamSubst = typeParamSubst.bindT((oldParam.name, ScalaPsiUtil.getPsiElementId(oldParam)),
                  new ScTypeParameterType(newParam, subst))
            }
            fun.returnType.toOption.map(typeParamSubst.followed(subst).subst)
          case Some((fun: ScSyntheticFunction, subst)) =>
            var typeParamSubst = ScSubstitutor.empty
            fun.typeParameters.zip(typeParameters).foreach {
              case (oldParam: ScSyntheticTypeParameter, newParam: ScTypeParam) =>
                typeParamSubst = typeParamSubst.bindT((oldParam.name, ScalaPsiUtil.getPsiElementId(oldParam)),
                  new ScTypeParameterType(newParam, subst))
            }
            Some(subst.subst(fun.retType))
          case Some((fun: PsiMethod, subst)) =>
            var typeParamSubst = ScSubstitutor.empty
            fun.getTypeParameters.zip(typeParameters).foreach {
              case (oldParam: PsiTypeParameter, newParam: ScTypeParam) =>
                typeParamSubst = typeParamSubst.bindT((oldParam.name, ScalaPsiUtil.getPsiElementId(oldParam)),
                  new ScTypeParameterType(newParam, subst))
            }
            Some(typeParamSubst.followed(subst).subst(fun.getReturnType.toScType(getProject, getResolveScope)))
          case _ => None
        }
        superReturnType
    }
  }

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset
  def hasParameterClause: Boolean = {
    if (effectiveParameterClauses.nonEmpty) return true
    superMethod match {
      case Some(fun: ScFunction) => fun.hasParameterClause
      case Some(psi: PsiMethod) => true
      case None => false
    }
  }

  /**
   * Signature has repeated param, which is not the last one
   */
  def hasMalformedSignature: Boolean = {
    val clausesIterator = paramClauses.clauses.iterator
    while (clausesIterator.hasNext) {
      val clause = clausesIterator.next()
      val paramsIterator = clause.parameters.iterator
      while (paramsIterator.hasNext) {
        val param = paramsIterator.next()
        if (paramsIterator.hasNext && param.isRepeatedParameter) return true
      }
    }
    false
  }

  def definedReturnType: TypeResult[ScType] = {
    returnTypeElement match {
      case Some(ret) => ret.getType(TypingContext.empty)
      case _ if !hasAssign => Success(types.Unit, Some(this))
      case _ =>
        superMethod match {
          case Some(f: ScFunction) => f.definedReturnType
          case Some(m: PsiMethod) =>
            Success(m.getReturnType.toScType(getProject, getResolveScope), Some(this))
          case _ => Failure("No defined return type", Some(this))
        }
    }
  }

  /**
   * Returns pure 'function' type as it was defined as a field with functional value
   */
  def methodType(result: Option[ScType]): ScType = {
    val clauses = effectiveParameterClauses
    val resultType = result match {
      case None => returnType.getOrAny
      case Some(x) => x
    }
    if (!hasParameterClause) return resultType
    val res = if (clauses.nonEmpty)
      clauses.foldRight[ScType](resultType){(clause: ScParameterClause, tp: ScType) =>
        new ScMethodType(tp, clause.getSmartParameters, clause.isImplicit)(getProject, getResolveScope)
      }
      else new ScMethodType(resultType, Seq.empty, false)(getProject, getResolveScope)
    res.asInstanceOf[ScMethodType]
  }

  /**
   * Returns internal type with type parameters.
   */
  def polymorphicType(result: Option[ScType] = None): ScType = {
    if (typeParameters.isEmpty) methodType(result)
    else ScTypePolymorphicType(methodType(result), typeParameters.map(new TypeParameter(_)))
  }

  /**
   * Optional Type Element, denotion function's return type
   * May be omitted for non-recursive functions
   */
  def returnTypeElement: Option[ScTypeElement] = {
    this match {
      case st: ScalaStubBasedElementImpl[_] =>
        val stub = st.getStub
        if (stub != null) {
          return stub.asInstanceOf[ScFunctionStub].getReturnTypeElement
        }
      case _ =>
    }
    findChild(classOf[ScTypeElement])
  }

  def returnTypeIsDefined: Boolean = !definedReturnType.isEmpty

  def hasExplicitType = returnTypeElement.isDefined

  def removeExplicitType() {
    val colon = children.find(_.getNode.getElementType == ScalaTokenTypes.tCOLON)
    (colon, returnTypeElement) match {
      case (Some(first), Some(last)) => deleteChildRange(first, last)
      case _ =>
    }
  }

  def paramClauses: ScParameters

  def parameterList: ScParameters = paramClauses // TODO merge

  def isProcedure = paramClauses.clauses.isEmpty

  def importantOrderFunction(): Boolean = false

  def returnType: TypeResult[ScType] = {
    if (importantOrderFunction()) {
      val parent = getParent
      val data = parent.getUserData(ScFunction.calculatedBlockKey)
      if (data != null) returnTypeInner
      else {
        val children = parent match {
          case stub: ScalaStubBasedElementImpl[_] if stub.getStub != null =>
            import scala.collection.JavaConverters._
            stub.getStub.getChildrenStubs.asScala.map(_.getPsi)
          case _ => parent.getChildren.toSeq
        }
        children.foreach {
          case fun: ScFunction if fun.importantOrderFunction() =>
            ProgressManager.checkCanceled()
            fun.returnTypeInner
          case _ =>
        }
        parent.putUserData(ScFunction.calculatedBlockKey, java.lang.Boolean.TRUE)
        returnTypeInner
      }
    } else returnTypeInner
  }

  def returnTypeInner: TypeResult[ScType]

  def declaredType: TypeResult[ScType] = wrap(returnTypeElement) flatMap (_.getType(TypingContext.empty))

  def clauses: Option[ScParameters] = Some(paramClauses)

  def paramTypes: Seq[ScType] = parameters.map {_.getType(TypingContext.empty).getOrNothing}

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def effectiveParameterClauses: Seq[ScParameterClause] = paramClauses.clauses ++ syntheticParamClause

  private def syntheticParamClause: Option[ScParameterClause] = {
    val hasImplicit = clauses.exists(_.clauses.exists(_.isImplicit))
    if (isConstructor) {
      containingClass match {
        case owner: ScTypeParametersOwner =>
          if (hasImplicit) None else ScalaPsiUtil.syntheticParamClause(owner, paramClauses, classParam = false)
        case _ => None
      }
    } else {
      if (hasImplicit) None else ScalaPsiUtil.syntheticParamClause(this, paramClauses, classParam = false)
    }
  }

  def declaredElements = Seq(this)

  /**
   * Seek parameter with appropriate name in appropriate parameter clause.
    *
    * @param name          parameter name
   * @param clausePosition = -1, effective clause number, if -1 then parameter in any explicit? clause
   */
  def getParamByName(name: String, clausePosition: Int = -1): Option[ScParameter] = {
    clausePosition match {
      case -1 =>
        parameters.find { case param =>
            ScalaPsiUtil.memberNamesEquals(param.name, name) ||
              param.deprecatedName.exists(ScalaPsiUtil.memberNamesEquals(_, name))
        }
      case i if i < 0 || i >= effectiveParameterClauses.length => None
      case _ =>
        effectiveParameterClauses.apply(clausePosition).effectiveParameters.find { case param =>
          ScalaPsiUtil.memberNamesEquals(param.name, name) ||
            param.deprecatedName.exists(ScalaPsiUtil.memberNamesEquals(_, name))
        }
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitFunction(this)
  }

  def getGetterOrSetterFunction: Option[ScFunction] = {
    containingClass match {
      case clazz: ScTemplateDefinition =>
        if (name.endsWith("_=")) {
          clazz.functions.find(_.name == name.substring(0, name.length - 2))
        } else if (!hasParameterClause) {
          clazz.functions.find(_.name == name + "_=")
        } else None
      case _ => None
    }
  }

  def isBridge: Boolean = {
    //todo: fix algorithm for annotation resolve to not resolve objects (if it's possible)
    //heuristic algorithm to avoid SOE in MixinNodes.build
    annotations.exists(annot => {
      annot.typeElement match {
        case s: ScSimpleTypeElement => s.reference match {
          case Some(ref) => ref.refName == "bridge"
          case _ => false
        }
        case _ => false
      }
    })
  }

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

  def getTypeParameterList = new FakePsiTypeParameterList(getManager, getLanguage, typeParameters.toArray, this)

  def hasTypeParameters = typeParameters.nonEmpty

  def getParameterList: ScParameters = paramClauses

  @tailrec
  private def isJavaVarargs: Boolean = {
    if (hasAnnotation("scala.annotation.varargs").isDefined) true
    else {
      superMethod match {
        case Some(f: ScFunction) => f.isJavaVarargs
        case Some(m: PsiMethod) => m.isVarArgs
        case _ => false
      }
    }
  }

  /**
   * @return Empty array, if containing class is null.
   */
  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  def getFunctionWrappers(isStatic: Boolean, isInterface: Boolean, cClass: Option[PsiClass] = None): Seq[ScFunctionWrapper] = {
    val buffer = new ArrayBuffer[ScFunctionWrapper]
    if (cClass.isDefined || containingClass != null) {
      buffer += new ScFunctionWrapper(this, isStatic, isInterface, cClass)
      for {
        clause <- clauses
        first <- clause.clauses.headOption
        if first.hasRepeatedParam
        if isJavaVarargs
      } {
        buffer += new ScFunctionWrapper(this, isStatic, isInterface, cClass, isJavaVarargs = true)
      }

      val params = parameters
      for (i <- params.indices if params(i).baseDefaultParam) {
        buffer += new ScFunctionWrapper(this, isStatic = isStatic || isConstructor, isInterface, cClass, forDefault = Some(i + 1))
      }
    }
    buffer.toSeq
  }

  def parameters: Seq[ScParameter] = paramClauses.params

  override def getIcon(flags: Int) = Icons.FUNCTION

  def getReturnType: PsiType = {
    if (DumbService.getInstance(getProject).isDumb || !SyntheticClasses.get(getProject).isClassesRegistered) {
      return null //no resolve during dumb mode or while synthetic classes is not registered
    }
    getReturnTypeImpl
  }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  private def getReturnTypeImpl: PsiType = {
    val tp = getType(TypingContext.empty).getOrAny
    def lift: ScType => PsiType = _.toPsiType(getProject, getResolveScope)
    tp match {
      case ScFunctionType(rt, _) => lift(rt)
      case _ => lift(tp)
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
      if (option.isEmpty) return None
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
    val withSelf = clazz.selfType.isDefined
    if (withSelf) {
      val signs = TypeDefinitionMembers.getSelfTypeSignatures(clazz).forName(ScalaPsiUtil.convertMemberName(name))._1
      signs.fastPhysicalSignatureGet(s) match {
        case Some(x) if x.info.namedElement == this => x.supers.map { _.info }
        case Some(x) => x.supers.filter {_.info.namedElement != this }.map { _.info } :+ x.info
        case None => signs.get(s) match {
          case Some(x) if x.info.namedElement == this => x.supers.map { _.info }
          case Some(x) => x.supers.filter {_.info.namedElement != this }.map { _.info } :+ x.info
          case None => Seq.empty
        }
      }
    } else {
      TypeDefinitionMembers.getSignatures(clazz).forName(ScalaPsiUtil.convertMemberName(name))._1.
        fastPhysicalSignatureGet(s) match {
        case Some(x) => x.supers.map { _.info }
        case None => Seq.empty
      }
    }
  }


  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def findDeepestSuperMethod: PsiMethod = {
    val s = superMethods
    if (s.isEmpty) null
    else s.last
  }

  def getReturnTypeElement = null

  def findSuperMethods(parentClass: PsiClass) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods(checkAccess: Boolean) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods = superMethods.toArray // TODO which other xxxSuperMethods can/should be implemented?

  def findDeepestSuperMethods = PsiMethod.EMPTY_ARRAY

  def getReturnTypeNoResolve: PsiType = PsiType.VOID

  def getPom = null

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) =
    new util.ArrayList[MethodSignatureBackedByPsiMethod]()

  def getSignature(substitutor: PsiSubstitutor) = MethodSignatureBackedByPsiMethod.create(this, substitutor)

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
                  arg.toPsiType(getProject, getResolveScope) match {
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

  def getType(ctx: TypingContext): TypeResult[ScType] = {
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

  def hasAssign = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tASSIGN)).nonEmpty

  def getHierarchicalMethodSignature: HierarchicalMethodSignature = {
    new HierarchicalMethodSignatureImpl(getSignature(PsiSubstitutor.EMPTY))
  }

  override def isDeprecated = {
    hasAnnotation("scala.deprecated").isDefined || hasAnnotation("java.lang.Deprecated").isDefined
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
      if (isSimilarMemberForNavigation(member, strictCheck = false)) buf += member
    }
    if (buf.isEmpty) this
    else if (buf.length == 1) buf(0)
    else {
      val filter = buf.filter(isSimilarMemberForNavigation(_, strictCheck = true))
      if (filter.isEmpty) buf(0)
      else filter(0)
    }
  }

  //Why not to use default value? It's not working in Scala...
  def getTypeNoImplicits(ctx: TypingContext): TypeResult[ScType] = getTypeNoImplicits(ctx, returnType)

  def getTypeNoImplicits(ctx: TypingContext, rt: TypeResult[ScType]): TypeResult[ScType] = {
    collectReverseParamTypesNoImplicits match {
      case Some(params) =>
        val project = getProject
        val resolveScope = getResolveScope
        rt.map(params.foldLeft(_)((res, params) => ScFunctionType(res, params)(project, resolveScope)))
      case None => Failure("no params", Some(this))
    }
  }

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  def collectReverseParamTypesNoImplicits: Option[Seq[Seq[ScType]]] = {
    var i = paramClauses.clauses.length - 1
    val res: ArrayBuffer[Seq[ScType]] = ArrayBuffer.empty
    while (i >= 0) {
      val cl = paramClauses.clauses.apply(i)
      if (!cl.isImplicit) {
        val paramTypes: Seq[TypeResult[ScType]] = cl.parameters.map(_.getType(TypingContext.empty))
        if (paramTypes.exists(_.isEmpty)) return None
        res += paramTypes.map(_.get)
      }
      i = i - 1
    }
    Some(res.toSeq)
  }

  override def modifiableReturnType: Option[ScType] = returnType.toOption
}

object ScFunction {
  object Name {
    val Apply = "apply"
    val Update = "update"

    val Unapply = "unapply"
    val UnapplySeq = "unapplySeq"

    val Foreach = "foreach"
    val Map = "map"
    val FlatMap = "flatMap"
    val Filter = "filter"
    val WithFilter = "withFilter"

    val Unapplies: Set[String] = Set(Unapply, UnapplySeq)
    val ForComprehensions: Set[String] = Set(Foreach, Map, FlatMap, Filter, WithFilter)
    val Special: Set[String] = Set(Apply, Update) ++ Unapplies ++ ForComprehensions
  }

  /** Is this function sometimes invoked without it's name appearing at the call site? */
  def isSpecial(name: String): Boolean = Name.Special(name)

  private val calculatedBlockKey: Key[java.lang.Boolean] = Key.create("calculated.function.returns.block")

  @tailrec
  def getCompoundCopy(pTypes: List[List[ScType]], tParams: List[TypeParameter], rt: ScType, fun: ScFunction): ScFunction = {
    fun match {
      case light: ScLightFunctionDeclaration => getCompoundCopy(pTypes, tParams, rt, light.fun)
      case light: ScLightFunctionDefinition  => getCompoundCopy(pTypes, tParams, rt, light.fun)
      case decl: ScFunctionDeclaration       => new ScLightFunctionDeclaration(pTypes, tParams, rt, decl)
      case definition: ScFunctionDefinition  => new ScLightFunctionDefinition(pTypes, tParams, rt, definition)
    }
  }
}