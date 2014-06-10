package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.{PsiModificationTracker, PsiTreeUtil}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.{toPsiClassExt, toPsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInterpolationPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.processor.{ExtractorResolveProcessor, BaseProcessor}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference

trait ResolvableStableCodeReferenceElement extends ScStableCodeReferenceElement {
  private object Resolver extends StableCodeReferenceElementResolver(this, false, false, false)
  private object ResolverAllConstructors extends StableCodeReferenceElementResolver(this, false, true, false)
  private object NoConstructorResolver extends StableCodeReferenceElementResolver(this, false, false, true)
  private object ShapesResolver extends StableCodeReferenceElementResolver(this, true, false, false)
  private object ShapesResolverAllConstructors extends StableCodeReferenceElementResolver(this, true, true, false)

  def multiResolve(incomplete: Boolean): Array[ResolveResult] = {
    CachesUtil.getMappedWithRecursionPreventingWithRollback[ResolvableStableCodeReferenceElement, Boolean, Array[ResolveResult]](
      this, incomplete, CachesUtil.RESOLVE_KEY, Resolver.resolve, Array.empty, PsiModificationTracker.MODIFICATION_COUNT)
  }

  protected def processQualifierResolveResult(res: ResolveResult, processor: BaseProcessor, ref: ScStableCodeReferenceElement) {
    res match {
      case r@ScalaResolveResult(td: ScTypeDefinition, substitutor) =>
        td match {
          case obj: ScObject =>
            val fromType = r.fromType match {
              case Some(fType) => Success(ScProjectionType(fType, obj, superReference = false), Some(this))
              case _ => td.getType(TypingContext.empty).map(substitutor.subst)
            }
            var state = ResolveState.initial.put(ScSubstitutor.key, substitutor)
            if (fromType.isDefined) {
              state = state.put(BaseProcessor.FROM_TYPE_KEY, fromType.get)
              processor.processType(fromType.get, ResolvableStableCodeReferenceElement.this, state)
            } else {
              td.processDeclarations(processor, state, null, ResolvableStableCodeReferenceElement.this)
            }
          case _: ScClass | _: ScTrait =>
            td.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, substitutor), null, ResolvableStableCodeReferenceElement.this)
        }
      case ScalaResolveResult(typed: ScTypedDefinition, s) =>
        val fromType = s.subst(typed.getType(TypingContext.empty).getOrElse(return))
        processor.processType(fromType, this, ResolveState.initial().put(BaseProcessor.FROM_TYPE_KEY, fromType))
        processor match {
          case p: ExtractorResolveProcessor =>
            if (processor.candidatesS.isEmpty) {
              //check implicit conversions
              val expr =
                ScalaPsiElementFactory.createExpressionWithContextFromText(ref.getText, ref.getContext, ref)
              //todo: this is really hacky solution... Probably can be joint somehow with interpolated pattern.
              expr match {
                case ref: ResolvableReferenceExpression =>
                  ref.doResolve(ref, processor, accessibilityCheck = true)
                case _ =>
              }
            }
          case _ => //do nothing
        }
      case ScalaResolveResult(field: PsiField, s) =>
        processor.processType(s.subst(ScType.create(field.getType, getProject, getResolveScope)), this)
      case ScalaResolveResult(clazz: PsiClass, s) =>
        processor.processType(new ScDesignatorType(clazz, true), this) //static Java import
      case ScalaResolveResult(pack: ScPackage, s) =>
        pack.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, s),
          null, ResolvableStableCodeReferenceElement.this)
      case other: ScalaResolveResult =>
        other.element.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, other.substitutor),
          null, ResolvableStableCodeReferenceElement.this)
      case _ =>
    }
  }

  def doResolve(ref: ScStableCodeReferenceElement, processor: BaseProcessor,
                accessibilityCheck: Boolean = true): Array[ResolveResult] = {
    val importStmt = PsiTreeUtil.getContextOfType(ref, true, classOf[ScImportStmt])

    if (importStmt != null) {
      val importHolder = PsiTreeUtil.getContextOfType(importStmt, true, classOf[ScImportsHolder])
      if (importHolder != null) {
        importHolder.getImportStatements.takeWhile(_ != importStmt).foreach {
          case stmt: ScImportStmt =>
            stmt.importExprs.foreach {
              case expr: ScImportExpr => expr.reference match {
                case Some(reference) => reference.resolve()
                case None => expr.qualifier.resolve()
              }
            }
        }
      }
    }
    if (!accessibilityCheck) processor.doNotCheckAccessibility()
    var x = false
    //performance improvement
    ScalaPsiUtil.fileContext(ref) match {
      case s: ScalaFile if s.isCompiled =>
        x = true
        //todo: improve checking for this and super
        val refText: String = ref.getText
        if (!refText.contains("this") && !refText.contains("super") && refText.contains(".")) {
          //so this is full qualified reference => findClass, or findPackage
          val facade = JavaPsiFacade.getInstance(getProject)
          val manager = ScalaPsiManager.instance(getProject)
          val classes = manager.getCachedClasses(ref.getResolveScope, refText)
          val pack = facade.findPackage(refText)
          if (pack != null) processor.execute(pack, ResolveState.initial)
          for (clazz <- classes) processor.execute(clazz, ResolveState.initial)
          val candidates = processor.candidatesS
          val filtered = candidates.filter(candidatesFilter)

          if (filtered.nonEmpty) return filtered.toArray
        }
      case _ =>
    }

    processQualifier(ref, processor)

    val candidates = processor.candidatesS
    val filtered = candidates.filter(candidatesFilter)
    if (accessibilityCheck && filtered.size == 0) return doResolve(ref, processor, accessibilityCheck = false)
    filtered.toArray
  }

  protected def processQualifier(ref: ScStableCodeReferenceElement, processor: BaseProcessor) {
    _qualifier() match {
      case None =>
        def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
          ProgressManager.checkCanceled()
          place match {
            case null =>
            case p: ScTypeElement if p.analog.isDefined =>
              // this allows the type elements in a context or view bound to be path-dependent types, based on parameters.
              // See ScalaPsiUtil.syntheticParamClause and StableCodeReferenceElementResolver#computeEffectiveParameterClauses
              treeWalkUp(p.analog.get, lastParent)
            case p =>
              if (!p.processDeclarations(processor,
                ResolveState.initial,
                lastParent, ref)) return
              place match {
                case (_: ScTemplateBody | _: ScExtendsBlock) => // template body and inherited members are at the same level.
                case _ => if (!processor.changedLevel) return
              }
              treeWalkUp(place.getContext, place)
          }
        }
        treeWalkUp(ref, null)
      case Some(p: ScInterpolationPattern) =>
        val expr =
          ScalaPsiElementFactory.createExpressionWithContextFromText(s"""_root_.scala.StringContext("").$refName""", p, ref)
        expr match {
          case ref: ResolvableReferenceExpression =>
            ref.doResolve(ref, processor, accessibilityCheck = true)
          case _ =>
        }
      case Some(q: ScDocResolvableCodeReference) =>
        q.multiResolve(incomplete = true).foreach(processQualifierResolveResult(_, processor, ref))
      case Some(q: ScStableCodeReferenceElement) =>
        q.bind() match {
          case Some(res) => processQualifierResolveResult(res, processor, ref)
          case _ =>
        }
      case Some(thisQ: ScThisReference) => for (ttype <- thisQ.getType(TypingContext.empty)) processor.processType(ttype, this)
      case Some(superQ: ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
    }
  }

  private def _qualifier() = {
    getContext match {
      case p: ScInterpolationPattern => Some(p)
      case sel: ScImportSelector =>
        sel.getContext /*ScImportSelectors*/.getContext.asInstanceOf[ScImportExpr].reference
      case _ => pathQualifier
    }
  }

  private def candidatesFilter(result: ScalaResolveResult) = {
    result.element match {
      case c: PsiClass if c.name == c.qualifiedName => c.getContainingFile match {
        case s: ScalaFile => true // scala classes are available from default package
        // Other classes from default package are available only for top-level Scala statements
        case _ => PsiTreeUtil.getContextOfType(this, true, classOf[ScPackaging]) == null
      }
      case _ => true
    }
  }

  def resolveNoConstructor: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    CachesUtil.getWithRecursionPreventingWithRollback(this, CachesUtil.NO_CONSTRUCTOR_RESOLVE_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableStableCodeReferenceElement) =>
        NoConstructorResolver.resolve(expr, incomplete = false))
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }

  def resolveAllConstructors: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    CachesUtil.getWithRecursionPreventingWithRollback(this, CachesUtil.REF_ELEMENT_RESOLVE_CONSTR_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableStableCodeReferenceElement) =>
        ResolverAllConstructors.resolve(expr, incomplete = false))
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }

  def shapeResolve: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    CachesUtil.getWithRecursionPreventingWithRollback(this, CachesUtil.REF_ELEMENT_SHAPE_RESOLVE_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableStableCodeReferenceElement) =>
        ShapesResolver.resolve(expr, incomplete = false))
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }

  def shapeResolveConstr: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    CachesUtil.getWithRecursionPreventingWithRollback(this, CachesUtil.REF_ELEMENT_SHAPE_RESOLVE_CONSTR_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableStableCodeReferenceElement) =>
        ShapesResolverAllConstructors.resolve(expr, incomplete = false))
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }
}