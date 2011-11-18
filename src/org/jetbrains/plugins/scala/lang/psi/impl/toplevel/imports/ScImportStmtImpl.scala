package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.openapi.util.Key
import com.intellij.lang.ASTNode

import lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import _root_.scala.collection.mutable.HashSet
import parser.ScalaElementTypes
import psi.stubs.ScImportStmtStub
import usages._
import com.intellij.openapi.progress.ProgressManager
import lang.resolve.processor._
import api.toplevel.typedef.ScTemplateDefinition
import collection.immutable.Set
import base.types.ScSimpleTypeElementImpl
import api.base.ScStableCodeReferenceElement
import types.result.Failure
import types.{ScSubstitutor, ScDesignatorType}
import org.jetbrains.plugins.scala.extensions._

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScImportStmtImpl extends ScalaStubBasedElementImpl[ScImportStmt] with ScImportStmt {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScImportStmtStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScImportStatement"

  import scope._

  def importExprs: Array[ScImportExpr] =
    getStubOrPsiChildren(ScalaElementTypes.IMPORT_EXPR, JavaArrayFactoryUtil.ScImportExprFactory)

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val importsIterator = importExprs.iterator
    while (importsIterator.hasNext) {
      val importExpr = importsIterator.next()
      ProgressManager.checkCanceled()
      if (importExpr == lastParent) return true
      def workWithImportExpr: Boolean = {
        val ref = importExpr.reference match {
          case Some(ref) => ref
          case _ => return true
        }
        val exprQual: ScStableCodeReferenceElement = importExpr.selectorSet match {
          case Some(_) => ref
          case None if importExpr.singleWildcard => ref
          case None => ref.qualifier.getOrElse(return true)
        }
        val refType = exprQual.bind() match {
          case Some(ScalaResolveResult(p: PsiPackage, _)) => Failure("no failure", Some(this))
          case _ => ScSimpleTypeElementImpl.calculateReferenceType(exprQual, false)
        }
        val resolve: Array[ResolveResult] = ref.multiResolve(false)
        val resolveIterator = resolve.iterator
        while (resolveIterator.hasNext) {
          val (elem, importsUsed, s) = resolveIterator.next() match {
            case s: ScalaResolveResult => (s.getElement, s.importsUsed, s.substitutor)
            case r: ResolveResult => (r.getElement, Set[ImportUsed](), ScSubstitutor.empty)
          }
          val subst = state.get(ScSubstitutor.key).toOption.getOrElse(ScSubstitutor.empty).followed(s)
          ProgressManager.checkCanceled()
          importExpr.selectorSet match {
            case None =>
              // Update the set of used imports
              val newImportsUsed = Set(importsUsed.toSeq: _*) + ImportExprUsed(importExpr)
              var newState: ResolveState = state.put(ImportUsed.key, newImportsUsed).put(ScSubstitutor.key, subst)
              refType.foreach { tp =>
                newState = newState.put(BaseProcessor.FROM_TYPE_KEY, tp)
              }
              if (importExpr.singleWildcard) {
                (elem, processor) match {
                  case (cl: PsiClass, processor: BaseProcessor) if !cl.isInstanceOf[ScTemplateDefinition] => {
                    if (!processor.processType(new ScDesignatorType(cl, true), place,
                      newState)) return false
                  }
                  case _ => {
                    if (!elem.processDeclarations(processor, newState, this, place)) return false
                  }
                }
              } else {
                if (!processor.execute(elem, newState)) return false
              }
            case Some(set) => {
              val shadowed: HashSet[(ScImportSelector, PsiElement)] = HashSet.empty
              set.selectors foreach {
                selector =>
                ProgressManager.checkCanceled()
                selector.reference.multiResolve(false) foreach {
                  result =>
                  //Resolve the name imported by selector
                  //Collect shadowed elements
                    shadowed += ((selector, result.getElement))
                    var newState: ResolveState = state.put(ResolverEnv.nameKey, selector.importedName).
                            put(ImportUsed.key, Set(importsUsed.toSeq: _*) + ImportSelectorUsed(selector)).
                            put(ScSubstitutor.key, subst)
                    refType.foreach {tp =>
                      newState = newState.put(BaseProcessor.FROM_TYPE_KEY, tp)
                    }
                    if (!processor.execute(result.getElement, newState)) {
                    return false
                  }
                }
              }

              // There is total import from stable id
              // import a.b.c.{d=>e, f=>_, _}
              if (set.hasWildcard) {
                processor match {
                  case bp: BaseProcessor => {
                    ProgressManager.checkCanceled()
                    val p1 = new BaseProcessor(bp.kinds) {
                      override def getHint[T](hintKey: Key[T]): T = processor.getHint(hintKey)

                      override def handleEvent(event: PsiScopeProcessor.Event, associated: Object) {
                        processor.handleEvent(event, associated)
                      }

                      override def execute(element: PsiElement, state: ResolveState): Boolean = {
                        // Register shadowing import selector
                        val elementIsShadowed = shadowed.find(p => element == p._2)

                        var newState = elementIsShadowed match {
                          case Some((selector, elem)) => {
                            val oldImports = state.get(ImportUsed.key)
                            val newImports = if (oldImports == null) Set[ImportUsed]() else oldImports

                            state.put(ImportUsed.key, Set(newImports.toSeq: _*) + ImportSelectorUsed(selector)).
                                  put(ScSubstitutor.key, subst)
                          }
                          case None => state.put(ScSubstitutor.key, subst)
                        }

                        refType.foreach {tp =>
                          newState = newState.put(BaseProcessor.FROM_TYPE_KEY, tp)
                        }

                        if (elementIsShadowed != None) true else processor.execute(element, newState)
                      }
                    }

                    val newImportsUsed: Set[ImportUsed] = Set(importsUsed.toSeq: _*) + ImportWildcardSelectorUsed(importExpr)
                    var newState: ResolveState = state.put(ImportUsed.key, newImportsUsed).put(ScSubstitutor.key, subst)
                    refType.foreach {tp =>
                      newState = newState.put(BaseProcessor.FROM_TYPE_KEY, tp)
                    }
                    (elem, processor) match {
                      case (cl: PsiClass, processor: BaseProcessor) if !cl.isInstanceOf[ScTemplateDefinition] => {
                        if (!processor.processType(new ScDesignatorType(cl, true), place.asInstanceOf[ScalaPsiElement],
                          newState)) return false
                      }
                      case _ => {
                        if (!elem.processDeclarations(p1,
                          // In this case import optimizer should check for used selectors
                          newState,
                          this, place)) return false
                      }
                    }
                  }
                  case _ => true
                }
              }
            }
          }
        }
        true
      }
      if (!workWithImportExpr) return false
    }
    true
  }
}