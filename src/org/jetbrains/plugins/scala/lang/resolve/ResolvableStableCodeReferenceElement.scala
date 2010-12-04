package org.jetbrains.plugins.scala
package lang
package resolve

import _root_.java.lang.String
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang._
import processor.{CompletionProcessor, BaseProcessor}
import psi.api.base._
import psi.api.expr._
import psi.api.toplevel.ScTypedDefinition
import psi.api.toplevel.templates.{ScTemplateBody, ScExtendsBlock}
import psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition, ScClass}
import psi.api.{ScPackage, ScalaFile}
import psi.impl.toplevel.synthetic.SyntheticClasses
import psi.api.toplevel.packaging.ScPackaging
import psi.ScalaPsiUtil
import psi.types._
import resolve._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import com.intellij.psi.impl._
import com.intellij.psi.PsiElement
import result.TypingContext
import caches.{CachesUtil, ScalaCachesManager}
import com.intellij.openapi.progress.ProgressManager

trait ResolvableStableCodeReferenceElement extends ScStableCodeReferenceElement {
  private object Resolver extends StableCodeReferenceElementResolver(this, false)
  private object ShapesResolver extends StableCodeReferenceElementResolver(this, true)

  def multiResolve(incomplete: Boolean) = {
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, Resolver, true, incomplete)
  }

  def processQualifierResolveResult(res: ResolveResult, processor: BaseProcessor, ref: ScStableCodeReferenceElement): Unit = {
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
        processor.processType(s.subst(typed.getType(TypingContext.empty).getOrElse(Any)), this)
      case ScalaResolveResult(field: PsiField, s) =>
        processor.processType(s.subst(ScType.create(field.getType, getProject, getResolveScope)), this)
      case ScalaResolveResult(clazz: PsiClass, s) => {
        processor.processType(new ScDesignatorType(clazz, true), this) //static Java import
      }
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
          val classes = facade.findClasses(refText, ref.getResolveScope)
          val pack = facade.findPackage(refText)
          if (pack != null) processor.execute(pack, ResolveState.initial)
          for (clazz <- classes) processor.execute(clazz, ResolveState.initial)
          val candidates = processor.candidates
          val filtered = candidates.filter(candidatesFilter)

          if (!filtered.isEmpty) {
            return filtered.toArray
          }
        }
      case _ =>
    }

    _qualifier match {
      case None => {
        def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
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
        val results = q.bind match {
          case Some(res) => processQualifierResolveResult(res, processor, ref)
          case _ =>
        }
      }
      case Some(thisQ: ScThisReference) => for (ttype <- thisQ.getType(TypingContext.empty)) processor.processType(ttype, this)
      case Some(superQ: ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
    }

    val candidates = processor.candidates
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

  @volatile
  private var shapeResolveResults: Array[ResolveResult] = null
  @volatile
  private var shapeResolveResultsModCount: Long = 0

  def shapeResolve: Array[ResolveResult] = {
    ProgressManager.checkCanceled
    var tp = shapeResolveResults
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && shapeResolveResultsModCount == curModCount) {
      return tp
    }
    tp = shapeResolveInner
    shapeResolveResults = tp
    shapeResolveResultsModCount = curModCount
    return tp
  }

  private def shapeResolveInner: Array[ResolveResult] = {
    ShapesResolver.resolve(this, false)
  }
}