package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiReferenceList.Role
import com.intellij.psi._
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.contextApplied.{ContextApplied, ContextAppliedUtil}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiReferenceList
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionImpl.isJavaVarargs
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScTopLevelStubBasedElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{JavaIdentifier, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ParameterizedType, TypeParameter, TypeParameterType, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, ScType, TermSignature}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData}

import javax.swing.Icon
import scala.annotation.tailrec

abstract class ScFunctionImpl[F <: ScFunction](stub: ScFunctionStub[F],
                                               nodeType: ScFunctionElementType[F],
                                               node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with ScTopLevelStubBasedElement[F, ScFunctionStub[F]]
    with ScMember
    with ScFunction
    with ScTypeParametersOwner
    with ContextApplied.SyntheticElementsOwner {

  override final def isStable: Boolean =
    if (effectiveParameterClauses.nonEmpty)
      false
    else
      this.isInScala3File && returnTypeElement.exists(_.singleton)

  override def nameId: PsiElement = {
    val n = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
      case null => getNode.findChildByType(ScalaTokenTypes.kTHIS)
      case notNull => notNull
    }
    if (n == null) {
      val stub = getGreenStub
      if (stub == null) {
        val message = ScalaBundle.message("both.stub.and.name.identifier.node.are.null", getClass.getSimpleName, getText)
        throw new NullPointerException(message)
      }
      return createIdentifier(getGreenStub.getName).getPsi
    }
    n.getPsi
  }

  @Cached(ModTracker.anyScalaPsiChange, this)
  override def paramClauses: ScParameters = getStubOrPsiChild(ScalaElementType.PARAM_CLAUSES)

  @CachedInUserData(this, BlockModificationTracker(this))
  override def syntheticContextAppliedDefs: Seq[ScalaPsiElement] =
    ContextAppliedUtil.createSyntheticElementsFor(this, this.containingClass, parameters, typeParameters)

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    if (lastParent == null) return true

    // process function's type parameters
    if (!super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place))
      return false

    // process synthetic context-applied decls
    if (!super[SyntheticElementsOwner].processDeclarations(processor, state, lastParent, place))
      return false

    processParameters(processor, state, lastParent)
  }

  private def processParameters(processor: PsiScopeProcessor,
                                state: ResolveState,
                                lastParent: PsiElement): Boolean = {

    if (lastParent != null && shouldProcessParameters(lastParent)) {
      for {
        clause <- effectiveParameterClauses
        param <- clause.effectiveParameters
      } {
        ProgressManager.checkCanceled()
        if (!processor.execute(param, state)) return false
      }
    }
    true
  }

  // to resolve parameters in return type, type parameter context bounds and body;
  // references in default parameters are processed in ScParametersImpl
  protected def shouldProcessParameters(lastParent: PsiElement): Boolean = {
    def isFromTypeParams = lastParent.isInstanceOf[ScTypeParamClause]

    //don't compare returnTypeElement with lastParent, they may be different instances due to caches/stubs
    def isReturnTypeElement = lastParent.isInstanceOf[ScTypeElement] && lastParent.getContext == this

    !lastParent.isPhysical || isFromTypeParams || isReturnTypeElement
  }

  @Cached(ModTracker.anyScalaPsiChange, this)
  override def returnTypeElement: Option[ScTypeElement] = byPsiOrStub(findChild[ScTypeElement])(_.typeElement)

  // TODO unify with ScValue and ScVariable
  protected override final def baseIcon: Icon = {
    syntheticNavigationElement match {
      case c: ScClass if ScalaPsiUtil.hasImplicitModifier(c) => return c.getIcon(flags = 0)
      case _ =>
    }

    var parent = getParent
    while (parent != null) {
      parent match {
        case _: ScExtendsBlock =>
          return if (isAbstractMember) PlatformIcons.ABSTRACT_METHOD_ICON else PlatformIcons.METHOD_ICON
        case _: ScBlock | _: ScalaFile | _: ScExtension  =>
          return Icons.FUNCTION
        case _ =>
          parent = parent.getParent
      }
    }
    null
  }

  override def getReturnType: PsiType = {
    if (DumbService.getInstance(getProject).isDumb || !SyntheticClasses.get(getProject).isClassesRegistered) {
      return null //no resolve during dumb mode or while synthetic classes is not registered
    }
    if(isConstructor) {
      null
    } else {
      getReturnTypeImpl
    }
  }

  @CachedInUserData(this, BlockModificationTracker(this))
  private def getReturnTypeImpl: PsiType = {
    val resultType = `type`().getOrAny match {
      case FunctionType(rt, _) => rt
      case tp => tp
    }
    resultType.toPsiType
  }

  override def definedReturnType: TypeResult = {
    def renameTypeParams(superM: PsiMethod): ScSubstitutor = {
      import org.jetbrains.plugins.scala.lang.psi.types.api.PsiTypeParametersExt
      val superTypeParams = superM match {
        case fn: ScFunction => fn.typeParametersWithExtension.map(TypeParameter(_))
        case _              => superM.getTypeParameters.instantiate
      }

      ScSubstitutor.bind(superTypeParams, this.typeParametersWithExtension)(TypeParameterType(_))
    }

    returnTypeElement match {
      case Some(ret)       => ret.`type`()
      case _ if !hasAssign => Right(Unit)
      case _ =>
        superMethodAndSubstitutor match {
          case Some((f: ScFunction, s)) =>
            val subst = renameTypeParams(f).followed(s)
            f.definedReturnType.map(subst)
          case Some((m: PsiMethod, s)) =>
            val subst = renameTypeParams(m).followed(s)
            Right(subst(m.getReturnType.toScType()))
          case _ => Failure(ScalaBundle.message("no.defined.return.type"))
        }
    }
  }

  override def hasUnitResultType: Boolean = {
    @tailrec
    def hasUnitRT(t: ScType): Boolean = t match {
      case _ if t.isUnit => true
      case ScMethodType(result, _, _) => hasUnitRT(result)
      case _ => false
    }
    this.returnType.exists(hasUnitRT)
  }

  override def hasParameterClause: Boolean = ScFunctionImpl.hasParameterClauseImpl(this)

  override def parameterListCount: Int = paramClauses.clauses.length

  def extensionMethodOwner: Option[ScExtension] = {
    val parent = getParent

    if (parent != null && parent.is[ScExtensionBody])
      parent.getParent.asOptionOf[ScExtension]
    else None
  }

  override def isExtensionMethod: Boolean =
    byStubOrPsi(_.isExtensionMethod)(extensionMethodOwner.nonEmpty)

  @CachedInUserData(this, BlockModificationTracker(this))
  override def effectiveParameterClauses: Seq[ScParameterClause] = {
    val maybeOwner = if (isConstructor) {
      containingClass match {
        case owner: ScTypeParametersOwner => Some(owner)
        case _                            => None
      }
    } else Option(this)

    paramClauses.clauses ++
      maybeOwner.flatMap {
        ScalaPsiUtil.syntheticParamClause(_, paramClauses, isClassParameter = false)()
      }
  }

  /**
    * @return Empty array, if containing class is null.
    */
  @Cached(BlockModificationTracker(this), this)
  override def getFunctionWrappers(isStatic: Boolean, isAbstract: Boolean, cClass: Option[PsiClass] = None): Seq[ScFunctionWrapper] = {
    val builder = Seq.newBuilder[ScFunctionWrapper]
    if (cClass.isDefined || containingClass != null) {
      for {
        clause <- clauses
        first  <- clause.clauses.headOption
        if first.hasRepeatedParam && isJavaVarargs(this)
      } builder += new ScFunctionWrapper(this, isStatic, isAbstract, cClass, isJavaVarargs = true)

      builder += new ScFunctionWrapper(this, isStatic, isAbstract, cClass)
    }
    builder.result()
  }

  // TODO Should be unified, see ScModifierListOwner
  override def hasModifierProperty(name: String): Boolean = {
    if (name == "abstract") {
      this match {
        case _: ScFunctionDeclaration =>
          containingClass match {
            case _: ScTrait => return true
            case c: ScClass if c.hasAbstractModifier => return true
            case _ =>
          }
        case _ =>
      }
    }
    super.hasModifierProperty(name)
  }

  override def hasAssign: Boolean = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tASSIGN)).nonEmpty

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  override def findDeepestSuperMethod: PsiMethod = {
    val s = superMethods
    if (s.isEmpty) null
    else s.last
  }

  override def getReturnTypeElement: Null = null

  override def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  //NOTE: Currently `checkAccess` parameter is unused.
  //Use it, if you find out that it's required in some place.
  //I added dummy implementation which returns `findSuperMethods` in order to make `NonExtendableApiUsageInspection` work
  //(under the hood it uses this version of overloaded method)
  override def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] = findSuperMethods

  override def findSuperMethods: Array[PsiMethod] = superMethods.toArray // TODO which other xxxSuperMethods can/should be implemented?

  override def findDeepestSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): java.util.List[MethodSignatureBackedByPsiMethod] =
    ContainerUtil.emptyList()

  override def getSignature(substitutor: PsiSubstitutor): MethodSignatureBackedByPsiMethod = MethodSignatureBackedByPsiMethod.create(this, substitutor)

  //todo implement me!
  override def isVarArgs = false

  override def isConstructor: Boolean = name == "this"

  override def getBody: PsiCodeBlock = null

  override def getThrowsList: FakePsiReferenceList = new FakePsiReferenceList(getManager, getLanguage, Role.THROWS_LIST) {
    override def getReferenceElements: Array[PsiJavaCodeReferenceElement] = {
      getReferencedTypes.map {
        tp => PsiElementFactory.getInstance(getProject).createReferenceElementByType(tp)
      }
    }

    override def getReferencedTypes: Array[PsiClassType] = {
      annotations("scala.throws").headOption match {
        case Some(annotation) =>
          annotation.constructorInvocation.args.map(_.exprs).getOrElse(Seq.empty).flatMap {
            _.`type`() match {
              case Right(ParameterizedType(des, Seq(arg))) => des.extractClass match {
                case Some(clazz) if clazz.qualifiedName == "java.lang.Class" =>
                  arg.toPsiType match {
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

  override def `type`(): TypeResult = {
    this.returnType match {
      case Right(tp) =>
        var res: TypeResult = Right(tp)
        val paramClauses = effectiveParameterClauses
        var i = paramClauses.length - 1
        while (i >= 0) {
          res match {
            case Right(t) =>
              val parameters = paramClauses.apply(i).effectiveParameters
              val paramTypes = parameters.map(_.`type`().getOrNothing)
              res = Right(FunctionType(t, paramTypes))
            case _ =>
          }
          i = i - 1
        }
        res
      case x => x
    }
  }

  override protected def isSimilarMemberForNavigation(m: ScMember, strictCheck: Boolean): Boolean = m match {
    case f: ScFunction => f.name == name && {
      if (strictCheck) new PhysicalMethodSignature(this, ScSubstitutor.empty).
        paramTypesEquiv(new PhysicalMethodSignature(f, ScSubstitutor.empty))
      else true
    }
    case _ => false
  }

  override def getHierarchicalMethodSignature: HierarchicalMethodSignature = {
    new HierarchicalMethodSignatureImpl(getSignature(PsiSubstitutor.EMPTY))
  }

  override def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq originalClass) return this
    if (!originalClass.is[ScTypeDefinition]) return this
    val c = originalClass.asInstanceOf[ScTypeDefinition]
    val membersIterator = c.members.iterator
    var buf = List.empty[ScMember]
    while (membersIterator.hasNext) {
      val member = membersIterator.next()
      if (isSimilarMemberForNavigation(member, strictCheck = false)) {
        buf ::= member
      }
    }
    buf match {
      case Nil => this
      case one :: Nil => one
      case head :: _ =>
        buf.iterator
          .filter(isSimilarMemberForNavigation(_, strictCheck = true))
          .headOption
          .getOrElse(head)
    }
  }

  override def superMethods: Seq[PsiMethod] = superSignatures.map(_.namedElement).filterByType[PsiMethod]

  override def superMethod: Option[PsiMethod] = superMethodAndSubstitutor.map(_._1)

  override def superMethodAndSubstitutor: Option[(PsiMethod, ScSubstitutor)] = {
    val option = TypeDefinitionMembers.getSignatures(containingClass).forName(name).findNode(this)
    option
      .flatMap(_.primarySuper)
      .map(_.info)
      .collect {
        case p: PhysicalMethodSignature => (p.method, p.substitutor)
      }
  }

  override def superSignatures: Seq[TermSignature] =
    TypeDefinitionMembers.getSignatures(containingClass).forName(name).findNode(this) match {
      case Some(x) => x.supers.map { _.info }
      case None    => Seq.empty
    }

  override def superSignaturesIncludingSelfType: Seq[TermSignature] = {
    val clazz = containingClass
    if (clazz == null) return Seq.empty

    if (clazz.selfType.isDefined) {
      val signs   = TypeDefinitionMembers.getSelfTypeSignatures(clazz)
      val forName = signs.forName(name)

      forName.findNode(this) match {
        case Some(x) if x.info.namedElement == this => x.supers.map(_.info)
        case Some(x)                                => x.supers.filter(_.info.namedElement != this).map(_.info) :+ x.info
        case None =>
          forName.get(new PhysicalMethodSignature(this, ScSubstitutor.empty)) match {
            case Some(x) if x.info.namedElement == this => x.supers.map(_.info)
            case Some(x)                                => x.supers.filter(_.info.namedElement != this).map(_.info) :+ x.info
            case None                                   => Seq.empty
          }
      }
    } else superSignatures
  }
}

object ScFunctionImpl {

  @tailrec
  private def hasParameterClauseImpl(function: ScFunction): Boolean = {
    if (function.effectiveParameterClauses.nonEmpty) return true

    function.superMethod match {
      case Some(fun: ScFunction) => hasParameterClauseImpl(fun)
      case Some(_: PsiMethod) => true
      case None => false
    }
  }

  @tailrec
  private def isJavaVarargs(fun: ScFunction): Boolean = {
    if (fun.hasAnnotation("scala.annotation.varargs")) true
    else {
      fun.superMethod match {
        case Some(f: ScFunction) => isJavaVarargs(f)
        case Some(m: PsiMethod) => m.isVarArgs
        case _ => false
      }
    }
  }

}