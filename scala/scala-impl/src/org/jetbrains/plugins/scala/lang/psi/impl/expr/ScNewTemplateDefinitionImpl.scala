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
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScBegin, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{ScTemplateDefinitionImpl, TypeDefinitionMembers}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types._
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
    def filterTypeSignatures(aliases: Seq[ScTypeAlias]): Map[String, TypeAliasSignature] =
      aliases.flatMap { alias =>
        val sig = TypeAliasSignature(alias)

        if (alias.isPrivate || alias.isProtected) None
        else                                      Option((alias.name, sig))
      }.toMap

    def filterTermSignatures(terms: Seq[ScDeclaredElementsHolder]): Map[TermSignature, ScType] = {
      lazy val sigs = TypeDefinitionMembers.getSignatures(this)
      val termSigs  = ScCompoundType.signaturesFromPsi(terms)

      termSigs.filterNot { case (sig, _) =>
        val isAvalableOutside = sig.namedElement.nameContext match {
          case m: ScMember => !m.isPrivate && !m.isProtected
          case _           => false
        }

        !isAvalableOutside || {
          val maybeTpe = OverridingAnnotator.typeForSigElement(sig.namedElement)
          val supers =
            sigs
              .forName(sig.name)
              .findNode(sig.namedElement)
              .map(_.supers.map(sig => (sig.info.namedElement, sig.info.substitutor)))
              .getOrElse(Seq.empty)

            maybeTpe.exists(
              tpe =>
                supers.exists { case (superElem, subst) =>
                  val superTpe = OverridingAnnotator.typeForSigElement(superElem)
                  superTpe.exists(t => subst(t).equiv(tpe))
                }
            )
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

    val pt                 = this.expectedType()
    val superTypeElements  = extendsBlock.templateParents.fold(Seq.empty[ScTypeElement])(_.allTypeElements)
    val declaredSuperTypes = extendsBlock.superTypes

    /**
     * An anonymous class is local to the expression that creates it, so Scala 3 approximates its type
     * by one that doesn't mention the class. That approximation only keeps members which are already
     * declared by one of the parents, i.e. `new { def bar: Int = 1 }` is simply an `AnyRef`.
     * Only when the approximation doesn't conform to the expected type, the expression is ascribed to
     * the expected type instead, so that e.g. `val x: AnyRef { def bar: Int } = new { def bar = 1 }`
     * still works.
     *
     * See `TypeOps.classBound` and `Typer.ensureNoLocalRefs` in the Scala 3 compiler.
     */
    val keepsRefinement =
      !this.isInScala3File || {
        val approximation =
          declaredSuperTypes match {
            case Nil => api.AnyRef
            case List(one) => one
            case _   => ScCompoundType(declaredSuperTypes)
          }

        pt.exists(!approximation.conforms(_))
      }

    val superTypes =
      if (superTypeElements.isEmpty && this.isInScala3File && keepsRefinement) pt.toSeq
      else if (declaredSuperTypes.isEmpty)                                     Seq(api.AnyRef)
      else                                                                     declaredSuperTypes

    if (superTypeElements.length > 1 || (keepsRefinement && (termSignatures.nonEmpty || typeSignatures.nonEmpty))) {
      Right(
        if (keepsRefinement) ScCompoundType(superTypes, termSignatures, typeSignatures)
        else                 ScCompoundType(superTypes)
      )
    } else if (superTypeElements.length == 1) {
      superTypeElements.head.getNonValueType()
    } else superTypes.headOption.asTypeResult
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
