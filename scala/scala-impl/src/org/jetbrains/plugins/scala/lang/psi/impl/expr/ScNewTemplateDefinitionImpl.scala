package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.OverridingAnnotator
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{JavaConstructor, ScConstructorInvocation, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScBegin, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{ScTemplateDefinitionImpl, TypeDefinitionMembers}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

import javax.swing.Icon

final class ScNewTemplateDefinitionImpl(stub: ScTemplateDefinitionStub[ScNewTemplateDefinition],
                                        nodeType: ScTemplateDefinitionElementType[ScNewTemplateDefinition],
                                        node: ASTNode,
                                        debugName: String)
  extends ScTemplateDefinitionImpl(stub, nodeType, node, debugName)
    with ScNewTemplateDefinition with ScBegin {

  override protected def targetTokenType: ScalaTokenType = ScalaTokenType.NewKeyword

  override def getIcon(flags: Int): Icon = Icons.CLASS

  override def firstConstructorInvocation: Option[ScConstructorInvocation] =
    Option(extendsBlock)
      .flatMap(_.templateParents)
      .flatMap(_.firstParentClause)

  override protected def updateImplicitArguments(): Unit = {
    // for regular case implicits are owned by ScConstructor
    setImplicitArguments(desugaredApply.toSeq.flatMap(_.findImplicitArguments))
  }

  protected override def innerType: TypeResult = {
    /**
     * An anonymous class is local to the expression that creates it, so Scala 3 approximates its type
     * by one that doesn't mention the class. That approximation only keeps a member when it narrows a
     * member of one of the parents, so that `new Foo { override type X = Int }` of a `trait Foo { type X }`
     * is a `Foo { type X = Int }`, while `new { def bar: Int = 1 }` is simply an `AnyRef`.
     * Members of a `Selectable` parent are always kept, since selecting them is the whole point of it.
     *
     * See `TypeOps.classBound` in the Scala 3 compiler.
     *
     * Scala 2 keeps more, namely every member that isn't already a member of a parent, so that
     * `new Foo { def baz: Int = 1 }` of a `trait Foo` is a `Foo { def baz: Int }`. But a member that
     * merely implements or overrides an inherited one without narrowing its type is dropped as well,
     * so `new Foo { def bar: Int = 1 }` of a `trait Foo { def bar: Int }` is just a `Foo`.
     */
    def isRefinable(overridesMemberInParent: => Boolean, narrowsMemberInParent: => Boolean): Boolean =
      if (this.isInScala3File) definedExpectedType.nonEmpty || parentsAreSelectable || narrowsMemberInParent
      else                     !overridesMemberInParent || narrowsMemberInParent

    def filterTypeSignatures(aliases: Seq[ScTypeAlias]): Map[String, TypeAliasSignature] = {
      lazy val types = TypeDefinitionMembers.getTypes(this)

      aliases.flatMap { alias =>
        val sig = TypeAliasSignature(alias)

        // An alias that overrides an abstract type in a parent always narrows it
        def overridesTypeInParent = types.forName(alias.name).findNode(alias).exists(_.supers.nonEmpty)

        if (alias.isPrivate || alias.isProtected) None
        else if (!isRefinable(overridesTypeInParent, overridesTypeInParent)) None
        else Option((alias.name, sig))
      }.toMap
    }

    def filterTermSignatures(terms: Seq[ScDeclaredElementsHolder]): Map[TermSignature, ScType] = {
      lazy val sigs = TypeDefinitionMembers.getSignatures(this)
      val termSigs  = ScCompoundType.signaturesFromPsi(terms)

      termSigs.filterNot { case (sig, _) =>
        val isAvalableOutside = sig.namedElement.nameContext match {
          case m: ScMember => !m.isPrivate && !m.isProtected
          case _           => false
        }

        val supers =
          sigs
            .forName(sig.name)
            .findNode(sig.namedElement)
            .map(_.supers.map(sig => (sig.info.namedElement, sig.info.substitutor)))
            .getOrElse(Seq.empty)

        !isAvalableOutside || !isRefinable(supers.nonEmpty, narrowsInheritedMember(sig.namedElement, supers))
      }
    }

    /**
     * Whether the member narrows the type of a member it overrides, which is what makes it worth
     * keeping in the refinement: `new Foo { override def bar: Int = 1 }` of a `trait Foo { def bar: Any }`
     * is a `Foo { def bar: Int }`, while overriding with the very same type, as in
     * `new Object { override def toString: String = "" }`, adds nothing to `Object`.
     *
     * Corresponds to the conformance checks in `TypeOps.classBound` in the Scala 3 compiler.
     */
    def narrowsInheritedMember(member: PsiNamedElement, supers: Seq[(PsiNamedElement, ScSubstitutor)]): Boolean = {
      // The signatures of a member and the members it overrides only differ in their result type
      def resultType(named: PsiNamedElement): Option[ScType] = named match {
        case function: ScFunction => function.returnType.toOption
        case method: PsiMethod    => Option(method.getReturnType).map(_.toScType())
        case named                => OverridingAnnotator.typeForSigElement(named)
      }

      resultType(member).exists { memberType =>
        supers.exists { case (superMember, substitutor) =>
          resultType(superMember).map(substitutor(_)).exists { superType =>
            memberType.conforms(superType) && !superType.conforms(memberType)
          }
        }
      }
    }

    // Reliably prevent cases like SCL-17168
    if (extendsBlock.getTextLength == 0) {
      return Failure(ScalaBundle.message("empty.new.expression"))
    }

    desugaredApply match {
      case Some(expr) => return expr.getNonValueType()
      case _          =>
    }

    val earlyHolders: Seq[ScDeclaredElementsHolder] = extendsBlock.earlyDefinitions match {
      case Some(e: ScEarlyDefinitions) =>
        e.members.flatMap {
          case holder: ScDeclaredElementsHolder => Seq(holder)
          case _                                => Seq.empty
        }
      case None => Seq.empty
    }

    val (termSignatures, typeSignatures) =
      extendsBlock.templateBody match {
        case Some(b: ScTemplateBody) =>
          val termSigs = filterTermSignatures(b.holders ++ earlyHolders)
          val typeSigs = filterTypeSignatures(b.aliases)
          (termSigs, typeSigs)
        case None => (ScCompoundType.signaturesFromPsi(earlyHolders), Map.empty[String, TypeAliasSignature])
      }

    val pt                 = definedExpectedType
    val superTypeElements  = extendsBlock.templateParents.fold(Seq.empty[ScTypeElement])(_.allTypeElements)
    val declaredSuperTypes = extendsBlock.superTypes

    val superTypes =
      if (superTypeElements.isEmpty && this.isInScala3File && pt.nonEmpty) pt.toSeq
      else if (declaredSuperTypes.isEmpty)                                 Seq(api.AnyRef)
      else                                                                 declaredSuperTypes

    if (superTypeElements.length > 1 || termSignatures.nonEmpty || typeSignatures.nonEmpty)
      Right(ScCompoundType(superTypes, termSignatures, typeSignatures))
    else if (superTypeElements.length == 1)
      superTypeElements.head.getNonValueType()
    else
      superTypes.headOption.asTypeResult
  }

  /**
   * The expected type, but only if it is fully defined. An expected type that is still an undetermined
   * type parameter tells us nothing about the anonymous class, and Scala 3 ignores it as well.
   *
   * See `isFullyDefined(pt, ForceDegree.none)` in `Typer.ensureNoLocalRefs` in the Scala 3 compiler.
   */
  private def definedExpectedType: Option[ScType] =
    this.expectedType().filterNot(_.subtypeExists {
      case _: ScAbstractType | _: api.UndefinedType | _: api.TypeParameterType => true
      case _                                                                  => false
    })

  /** Whether the members declared by this anonymous class are reachable through a `Selectable` parent. */
  private def parentsAreSelectable: Boolean = {
    val declaredSuperTypes = extendsBlock.superTypes

    val parents =
      if (declaredSuperTypes.lengthCompare(1) <= 0) declaredSuperTypes.headOption.getOrElse(api.AnyRef)
      else                                          ScCompoundType(declaredSuperTypes)

    TypeDefinitionMembers.isSelectable(parents)
  }

  override def desugaredApply: Option[ScExpression] = {
    if (firstConstructorInvocation.forall(_.arguments.size <= 1)) None else cachedInUserData("desugaredApply", this, BlockModificationTracker(this)) {
      //It's very rare case, when we need to desugar `.apply` first.
      val resolvedConstructor = firstConstructorInvocation.flatMap(_.reference).flatMap(_.resolve().toOption)
      val constrParamLength = resolvedConstructor.map {
        case ScalaConstructor(constr)         => constr.effectiveParameterClauses.length
        case JavaConstructor(_)               => 1
        case _                                => -1
      }
      val excessArgs =
        for {
          arguments   <- firstConstructorInvocation.map(_.arguments)
          paramLength <- constrParamLength
          if paramLength >= 0
        } yield {
          arguments.drop(paramLength)
        }

      excessArgs match {
        case Some(args) if args.nonEmpty =>
          val desugaredText = {
            val firstArgListOfApply = args.head
            val startOffsetInThis   = firstArgListOfApply.getTextRange.getStartOffset - this.getTextRange.getStartOffset

            val thisText            = getText
            val newTemplateDefText  = thisText.substring(0, startOffsetInThis)
            val applyArgsText       = thisText.substring(startOffsetInThis)

            s"($newTemplateDefText)$applyArgsText"
          }

          createExpressionWithContextFromText(desugaredText, getContext, this).toOption
        case _ => None
      }
    }
 }

 override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor, state: ResolveState,
                                          lastParent: PsiElement, place: PsiElement): Boolean =
  extendsBlock.templateBody match {
    case Some(body) if PsiTreeUtil.isContextAncestor(body, place, false) =>
      super.processDeclarationsForTemplateBody(processor, state, lastParent, place)
    case _ => true
  }

  override def nameId: PsiElement = null
  override def setName(name: String): PsiElement = throw new IncorrectOperationException("cannot set name")
  override def name: String = ScNamedElement.AnonymousPlaceholder
  override def getTextOffset: Int = extendsBlock.getTextOffset

  override def getName: String = name

  override def getSupers: Array[PsiClass] = {
    val effectiveSupers = extendsBlock.supers.filter(_ != this)
    val (interfaces, classes) = effectiveSupers.partition(_.isInterface)
    (classes ++ interfaces).toArray
  }

  override def getSuperTypes: Array[PsiClassType] = {
    val effectiveSuperTypes = extendsBlock.superTypes
    val (interfaceTypes, classTypes) = effectiveSuperTypes.partition { scalaType =>
      val clazz = scalaType.extractClass
      clazz.exists(_.isInterface)
    }
    toPsiClassTypes(classTypes ++ interfaceTypes)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    processDeclarationsImpl(processor, state, lastParent, place)

  override def getExtendsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def getImplementsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def getTypeWithProjections(thisProjections: Boolean = false): TypeResult = `type`() //no projections for new template definition

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitNewTemplateDefinition(this)
  }

  override protected def isInterface(namedElement: PsiNamedElement): Boolean = false

  override def psiMethods: Array[PsiMethod] = cachedInUserData("psiMethods", this, ModTracker.libraryAware(this)) {
    getAllMethods.filter(_.containingClass == this)
  }

  override protected def keywordTokenType: IElementType = ScalaTokenType.NewKeyword

  override protected def endParent: Option[PsiElement] = extendsBlock.templateBody

  override def isAnonymous: Boolean = extendsBlock.templateBody.nonEmpty

  override def isEffectivelyFinal: Boolean = true
}
