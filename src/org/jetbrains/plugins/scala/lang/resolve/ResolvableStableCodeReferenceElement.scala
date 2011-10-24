package org.jetbrains.plugins.scala
package lang
package resolve

import processor.BaseProcessor
import psi.api.base._
import psi.api.expr._
import psi.api.toplevel.ScTypedDefinition
import psi.api.toplevel.templates.{ScTemplateBody, ScExtendsBlock}
import psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition, ScClass}
import psi.api.toplevel.packaging.ScPackaging
import psi.ScalaPsiUtil
import psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import com.intellij.psi.impl._
import com.intellij.psi.PsiElement
import result.TypingContext
import com.intellij.openapi.progress.ProgressManager
import util.{PsiModificationTracker, PsiTreeUtil}
import caches.CachesUtil
import psi.api.{ScPackage, ScalaFile}
import psi.impl.ScalaPsiManager

trait ResolvableStableCodeReferenceElement extends ScStableCodeReferenceElement {
  private object Resolver extends StableCodeReferenceElementResolver(this, false, false, false)
  private object ResolverAllConstructors extends StableCodeReferenceElementResolver(this, false, true, false)
  private object NoConstructorResolver extends StableCodeReferenceElementResolver(this, false, false, true)
  private object ShapesResolver extends StableCodeReferenceElementResolver(this, true, false, false)
  private object ShapesResolverAllConstructors extends StableCodeReferenceElementResolver(this, true, true, false)

  def multiResolve(incomplete: Boolean) = {
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, Resolver, true, incomplete)
  }

  def processQualifierResolveResult(res: ResolveResult, processor: BaseProcessor, ref: ScStableCodeReferenceElement) {
    res match {
      case ScalaResolveResult(td: ScTypeDefinition, substitutor) => {
        td match {
          case _: ScObject =>
            td.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, substitutor),
              null, ResolvableStableCodeReferenceElement.this)
          case _: ScClass | _: ScTrait => td.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, substitutor),
            null, ResolvableStableCodeReferenceElement.this)
        }
      }
      case ScalaResolveResult(typed: ScTypedDefinition, s) =>
        processor.processType(s.subst(typed.getType(TypingContext.empty).getOrAny), this)
      case ScalaResolveResult(field: PsiField, s) =>
        processor.processType(s.subst(ScType.create(field.getType, getProject, getResolveScope)), this)
      case ScalaResolveResult(clazz: PsiClass, s) => {
        processor.processType(new ScDesignatorType(clazz, true), this) //static Java import
      }
      case ScalaResolveResult(pack: ScPackage, s) =>
        pack.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, s),
          null, ResolvableStableCodeReferenceElement.this, true)
      case other: ScalaResolveResult => {
        other.element.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, other.substitutor),
          null, ResolvableStableCodeReferenceElement.this)
      }
      case _ =>
    }
  }

  def doResolve(ref: ScStableCodeReferenceElement, processor: BaseProcessor): Array[ResolveResult] = {
    var x = false
    //performance improvement
    ScalaPsiUtil.fileContext(ref) match {
      case s: ScalaFile if s.isCompiled =>
        x = true
        //todo: improve checking for this and super
        val refText: String = ref.getText
        if (refText.contains("this") || refText.contains("super") || !refText.contains(".")) {} //do nothing
        else {
          //so this is full qualified reference => findClass, or findPackage
          val facade = JavaPsiFacade.getInstance(getProject)
          val manager = ScalaPsiManager.instance(getProject)
          val classes = manager.getCachedClasses(ref.getResolveScope, refText)
          val pack = facade.findPackage(refText)
          if (pack != null) processor.execute(pack, ResolveState.initial)
          for (clazz <- classes) processor.execute(clazz, ResolveState.initial)
          val candidates = processor.candidatesS
          val filtered = candidates.filter(candidatesFilter)

          if (!filtered.isEmpty) {
            return filtered.toArray
          }
        }
      case _ =>
    }

    _qualifier() match {
      case None => {
        def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
          ProgressManager.checkCanceled()
          place match {
            case null =>
            case p => {
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
        }
        treeWalkUp(ref, null)
      }
      case Some(q: ScStableCodeReferenceElement) => {
        q.bind() match {
          case Some(res) => processQualifierResolveResult(res, processor, ref)
          case _ =>
        }
      }
      case Some(thisQ: ScThisReference) => for (ttype <- thisQ.getType(TypingContext.empty)) processor.processType(ttype, this)
      case Some(superQ: ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
    }

    val candidates = processor.candidatesS
    val filtered = candidates.filter(candidatesFilter)

    filtered.toArray
  }

  private def _qualifier() = {
    getContext match {
      case sel: ScImportSelector => {
        sel.getContext /*ScImportSelectors*/.getContext.asInstanceOf[ScImportExpr].reference
      }
      case _ => pathQualifier
    }
  }

  private def candidatesFilter(result: ScalaResolveResult) = {
    result.element match {
      case c: PsiClass if c.getName == c.getQualifiedName => c.getContainingFile match {
        case s: ScalaFile => true // scala classes are available from default package
        // Other classes from default package are available only for top-level Scala statements
        case _ => PsiTreeUtil.getContextOfType(this, true, classOf[ScPackaging]) == null && (getContainingFile match {
          case s: ScalaFile => s.getPackageName.length == 0
          case _ => true
        })
      }
      case _ => true
    }
  }

  def resolveNoConstructor: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    CachesUtil.getWithRecurisionPreventing(this, CachesUtil.NO_CONSTRUCTOR_RESOLVE_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableStableCodeReferenceElement) =>
        NoConstructorResolver.resolve(this, false))
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }

  def resolveAllConstructors: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    CachesUtil.getWithRecurisionPreventing(this, CachesUtil.REF_ELEMENT_RESOLVE_CONSTR_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableStableCodeReferenceElement) =>
        ResolverAllConstructors.resolve(this, false))
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }

  def shapeResolve: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    CachesUtil.getWithRecurisionPreventing(this, CachesUtil.REF_ELEMENT_SHAPE_RESOLVE_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableStableCodeReferenceElement) =>
        ShapesResolver.resolve(this, false))
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }

  def shapeResolveConstr: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    CachesUtil.getWithRecurisionPreventing(this, CachesUtil.REF_ELEMENT_SHAPE_RESOLVE_CONSTR_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableStableCodeReferenceElement) =>
        ShapesResolverAllConstructors.resolve(this, false))
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }
}