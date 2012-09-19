package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.openapi.util.Key
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import _root_.scala.collection.mutable.HashSet
import parser.ScalaElementTypes
import psi.stubs.ScImportStmtStub
import usages._
import com.intellij.openapi.progress.ProgressManager
import lang.resolve.processor._
import api.toplevel.typedef.{ScObject, ScTypeDefinition, ScTemplateDefinition}
import collection.immutable.Set
import base.types.ScSimpleTypeElementImpl
import api.base.ScStableCodeReferenceElement
import types.result.{TypingContext, Failure}
import types.{ScSubstitutor, ScDesignatorType}
import org.jetbrains.plugins.scala.extensions._
import settings.ScalaProjectSettings
import lang.resolve.{StdKinds, ScalaResolveResult}
import completion.ScalaCompletionUtil
import caches.ScalaShortNamesCacheManager
import util.PsiTreeUtil

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
        val nameHint = processor.getHint(NameHint.KEY)
        val name = if (nameHint == null) "" else nameHint.getName(state)
        if (name != "" && !importExpr.singleWildcard) {
          val decodedName = ScalaPsiUtil.convertMemberName(name)
          importExpr.selectorSet match {
            case Some(set) => set.selectors.
                find(selector => ScalaPsiUtil.convertMemberName(selector.reference.refName) == decodedName) != None
            case None => if (ScalaPsiUtil.convertMemberName(ref.refName) != decodedName) return true
          }
        }
        val checkWildcardImports = processor match {
          case r: ResolveProcessor =>
            if (!r.checkImports()) return true
            r.checkWildcardImports()
          case _ => true
        }
        val exprQual: ScStableCodeReferenceElement = importExpr.selectorSet match {
          case Some(_) => ref
          case None if importExpr.singleWildcard => ref
          case None => ref.qualifier.getOrElse(return true)
        }
        val resolve: Array[ResolveResult] = ref.multiResolve(false)
        val refType = exprQual.bind() match {
          case Some(ScalaResolveResult(p: PsiPackage, _)) =>
            resolve.find {
              case ScalaResolveResult(elem, _) =>
                PsiTreeUtil.getContextOfType(elem, true, classOf[ScTypeDefinition]) match {
                  case obj: ScObject if obj.isPackageObject => true
                  case _ => false
                }
              case _ => false
            } match {
              case Some(_) =>
                val po = ScalaShortNamesCacheManager.getInstance(getProject).getPackageObjectByName(p.getQualifiedName, getResolveScope)
                if (po != null) {
                  po.getType(TypingContext.empty)
                } else Failure("no failure", Some(this))
              case _ => Failure("no failure", Some(this))
            }
          case _ => ScSimpleTypeElementImpl.calculateReferenceType(exprQual, shapesOnly = false)
        }
        val resolveIterator = resolve.iterator
        while (resolveIterator.hasNext) {
          val (elem, importsUsed, s) = resolveIterator.next() match {
            case s: ScalaResolveResult => (s.getElement, s.importsUsed, s.substitutor)
            case r: ResolveResult => (r.getElement, Set[ImportUsed](), ScSubstitutor.empty)
          }
          if (elem.isInstanceOf[PsiPackage] && processor.isInstanceOf[CompletionProcessor] &&
            processor.asInstanceOf[CompletionProcessor].includePrefixImports) {
            val prefixImports = ScalaProjectSettings.getInstance(getProject).getImportsWithPrefix.filter(s =>
              !s.startsWith(ScalaProjectSettings.EXCLUDE_PREFIX) &&
                s.substring(0, s.lastIndexOf(".")) == elem.asInstanceOf[PsiPackage].getQualifiedName
            )
            val excludeImports = ScalaProjectSettings.getInstance(getProject).getImportsWithPrefix.filter(s =>
              s.startsWith(ScalaProjectSettings.EXCLUDE_PREFIX) &&
                s.substring(ScalaProjectSettings.EXCLUDE_PREFIX.length, s.lastIndexOf(".")) == elem.asInstanceOf[PsiPackage].getQualifiedName
            )
            val names = new HashSet[String]()
            for (prefixImport <- prefixImports) {
              names += prefixImport.substring(prefixImport.lastIndexOf('.') + 1)
            }
            val excludeNames = new HashSet[String]()
            for (prefixImport <- excludeImports) {
              excludeNames += prefixImport.substring(prefixImport.lastIndexOf('.') + 1)
            }
            val wildcard = names.contains("_")
            def isOK(name: String): Boolean = {
              if (wildcard) !excludeNames.contains(name)
              else names.contains(name)
            }
            val newImportsUsed = Set(importsUsed.toSeq: _*) + ImportExprUsed(importExpr)
            val newState = state.put(ScalaCompletionUtil.PREFIX_COMPLETION_KEY, true).put(ImportUsed.key, newImportsUsed)
            elem.processDeclarations(new BaseProcessor(StdKinds.stableImportSelector) {
              def execute(element: PsiElement, state: ResolveState): Boolean = {
                element match {
                  case elem: PsiNamedElement if isOK(elem.name) => processor.execute(element, state)
                  case _ => true
                }
              }
            }, newState, this, place)
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
                if (!checkWildcardImports) return true
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
                if (!checkWildcardImports) return true
                processor match {
                  case bp: BaseProcessor => {
                    ProgressManager.checkCanceled()
                    val p1 = new BaseProcessor(bp.kinds) {
                      override def getHint[T](hintKey: Key[T]): T = processor.getHint(hintKey)

                      override def handleEvent(event: PsiScopeProcessor.Event, associated: Object) {
                        processor.handleEvent(event, associated)
                      }

                      override def getClassKind: Boolean = bp.getClassKind

                      override def setClassKind(b: Boolean) {
                        bp.setClassKind(b)
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
                        if (!processor.processType(new ScDesignatorType(cl, true), place, newState)) return false
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