package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createNewLineNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ScDeclarationSequenceHolder, ScImportsHolder, ScalaPsiUtil}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

trait ScBlock extends ScExpression
  with ScDeclarationSequenceHolder
  with ScImportsHolder
  with ScOptionalBracesOwner
{
  protected override def innerType: TypeResult = {
    if (hasCaseClauses) {
      val caseClauses = findChild[ScCaseClauses].get
      val clauses: Seq[ScCaseClause] = caseClauses.caseClauses

      val clausesTypes = ArrayBuffer[ScType]()
      val iterator = clauses.iterator
      while (iterator.hasNext) {
        iterator.next().expr match {
          case Some(e) => clausesTypes += e.`type`().getOrNothing
          case _ =>
        }
      }

      val clausesLubType =
        if (clausesTypes.isEmpty) Nothing
        else                      clausesTypes.lub()

      implicit val resolveScope: GlobalSearchScope = this.resolveScope

      getContext match {
        case _: ScCatchBlock =>
          val manager = ScalaPsiManager.instance
          val funs = manager.getCachedClasses(resolveScope, PartialFunctionType.TypeName)
          val fun = funs.find(_.is[ScTrait]).getOrElse(return Failure(ScalaBundle.message("cannot.find.partialfunction.class")))
          val throwable = manager.getCachedClass(resolveScope, "java.lang.Throwable").orNull
          if (throwable == null) return Failure(ScalaBundle.message("cannot.find.throwable.class"))
          return Right(ScParameterizedType(ScDesignatorType(fun), Seq(ScDesignatorType(throwable), clausesLubType)))
        case _ =>
          val et = this.expectedType(fromUnderscore = false)
            .getOrElse(return Failure(ScalaBundle.message("cannot.infer.type.without.expected.type")))

          val paramInfo = extractExpectedTypeParams(et)

          return paramInfo match {
            case Some((params, isPartial)) =>
              val sanitizedParams = params.map(_.removeVarianceAbstracts())

              val tpe =
                if (isPartial) PartialFunctionType(clausesLubType, sanitizedParams.head)
                else           FunctionType(clausesLubType, sanitizedParams)

              Right(tpe)
            case None =>
              Failure(ScalaBundle.message("cannot.infer.type.without.function.expected.type"))
          }
      }
    }
    val inner = resultExpression match {
      case None =>
        ScalaPsiUtil.fileContext(this) match {
          case scalaFile: ScalaFile if scalaFile.isCompiled => Nothing
          case _ => Unit
        }
      case Some(e) => avoidLocals(e.`type`().getOrAny)
    }
    Right(inner)
  }

  /**
   * Type avoidance at the block boundary (scalac's `packedType`, SLS §6.11): a
   * definition local to this block must not appear in the block's type. Each
   * occurrence is abstracted existentially, as `T forSome { Q }` over the local
   * definitions, then simplified as SLS §3.2.12 does: a covariant occurrence becomes
   * its upper bound, a contravariant one its lower bound (`Nothing`), and an invariant
   * one stays quantified.
   *  - a local stable `val X: T` (the singleton `X.type`, e.g. from a `this.type`
   *    member) is bounded by `T with Singleton`, and widens to `T` covariantly;
   *  - a local class or object is bounded by its parents: `{ class C extends Base; new C }`
   *    is a `Base`, and `new Ref(new C)` a `Ref[_1] forSome { type _1 <: Base }`.
   * Iterated, because a bound may mention another local definition.
   */
  private def avoidLocals(tpe: ScType): ScType = {
    def isLocal(e: PsiElement): Boolean = PsiTreeUtil.isAncestor(this, e, /*strict*/ true)

    // (covariant replacement, upper bound of the existential) for a local occurrence
    def local(t: ScType): Option[(ScType, ScType)] = t match {
      case owner: DesignatorOwner if owner.isSingleton && isLocal(owner.element) =>
        owner.element match {
          case obj: ScObject =>
            val parents = parentsOf(obj)
            Some((parents, ScCompoundType(Seq(parents, Singleton))))
          case _ =>
            val widened = t.widen
            Some((widened, ScCompoundType(Seq(widened, Singleton))))
        }
      case ScDesignatorType(td: ScTypeDefinition) if isLocal(td) =>
        val parents = parentsOf(td)
        Some((parents, parents))
      case ParameterizedType(ScDesignatorType(td: ScTypeDefinition), _) if isLocal(td) =>
        val parents = parentsOf(td)
        Some((parents, parents))
      case _ => None
    }

    def packOnce(t: ScType): ScType = {
      val wildcards = scala.collection.mutable.LinkedHashMap.empty[ScType, ScExistentialArgument]
      val updated = t.recursiveVarianceUpdate() { (tp: ScType, v: Variance) =>
        local(tp) match {
          case Some((covariant, upper)) =>
            if (v.isCovariant) ReplaceWith(covariant)
            else if (v.isContravariant) ReplaceWith(Nothing)
            else ReplaceWith(wildcards.getOrElseUpdate(tp, ScExistentialArgument(s"_$$${wildcards.size + 1}", Nil, Nothing, upper)))
          case None => ProcessSubtypes
        }
      }
      if (wildcards.isEmpty) updated else ScExistentialType(updated)
    }

    if (!tpe.subtypeExists(local(_).isDefined)) tpe
    else {
      var current  = tpe
      var continue = true
      var guard    = 0
      while (continue && guard < 8) {
        guard += 1
        val updated = packOnce(current)
        continue = updated != current && updated.subtypeExists(local(_).isDefined)
        current = updated
      }
      current
    }
  }

  /** The parents of a template, as one type: `Base`, or `Base with T`. */
  private def parentsOf(td: ScTemplateDefinition): ScType = td.superTypes match {
    case Seq()       => AnyRef
    case Seq(single) => single
    case several     => ScCompoundType(several)
  }

  @tailrec
  private def extractExpectedTypeParams(pt: ScType): Option[(Seq[ScType], Boolean)] = pt match {
    case FunctionType(_, params)       => Option(params -> false)
    case PartialFunctionType(_, param) => Option(Seq(param) -> true)
    case ContextFunctionType(ret, _)   => extractExpectedTypeParams(ret)
    case _                             => None
  }

  def hasCaseClauses: Boolean = false
  def isInCatchBlock: Boolean = getContext.is[ScCatchBlock]
  def isPartialFunction: Boolean = hasCaseClauses && !isInCatchBlock

  def exprs: Seq[ScExpression] = findChildren[ScExpression]
  def statements: Seq[ScBlockStatement] = findChildren[ScBlockStatement]

  def hasRBrace: Boolean = getRBrace.isDefined
  def hasLBrace: Boolean = getLBrace.isDefined

  def resultExpression: Option[ScExpression] = lastStatement.flatMap(_.asOptionOf[ScExpression])
  def lastStatement: Option[ScBlockStatement] = findLastChild[ScBlockStatement]

  def addDefinition(decl: ScMember, before: PsiElement): Boolean = {
    getNode.addChild(decl.getNode,before.getNode)
    getNode.addChild(createNewLineNode(), before.getNode)
    true
  }

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean =
    super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
      processDeclarationsFromImports(processor, state, lastParent, place)

  def needCheckExpectedType = true
}

object ScBlock {
  def unapplySeq(block: ScBlock): Option[Seq[ScBlockStatement]] = Option(block.statements)
}
