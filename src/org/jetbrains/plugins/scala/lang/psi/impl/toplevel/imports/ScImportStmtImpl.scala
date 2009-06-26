package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import com.intellij.openapi.util.Key
import com.intellij.lang.ASTNode

import com.intellij.util.ArrayFactory
import lang.resolve.{ScalaResolveResult, ResolverEnv, BaseProcessor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import _root_.scala.collection.mutable.HashSet
import parser.ScalaElementTypes
import psi.stubs.ScImportStmtStub
import usages._

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScImportStmtImpl extends ScalaStubBasedElementImpl[ScImportStmt] with ScImportStmt {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScImportStmtStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScImportStatement"

  import scope._

  def importExprs: Array[ScImportExpr] = getStubOrPsiChildren(ScalaElementTypes.IMPORT_EXPR, new ArrayFactory[ScImportExpr] {
    def create(count: Int): Array[ScImportExpr] = new Array[ScImportExpr](count)
  })

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (importExpr <- importExprs) {
      if (importExpr == lastParent) return true
      val elemsAndUsages = importExpr.reference match {
        case Some(ref) => ref.multiResolve(false).map {
          x => x match {
            case s: ScalaResolveResult => (s.getElement, s.importsUsed)
            case r: ResolveResult => (r.getElement, Set[ImportUsed]())
          }
        } 
        case _ => Seq.empty
      }
      for ((elem, importsUsed) <- elemsAndUsages) {
        importExpr.selectorSet match {
          case None =>
            // Update the set of used imports
            val newImportsUsed = Set(importsUsed.toSeq: _*) + ImportExprUsed(importExpr)
            if (importExpr.singleWildcard) {
              if (!elem.processDeclarations(processor, state.put(ImportUsed.key, newImportsUsed), this, place)) return false
            } else {
              if (!processor.execute(elem, state.put(ImportUsed.key, newImportsUsed))) return false
            }
          case Some(set) => {
            val shadowed: HashSet[(ScImportSelector, PsiElement)] = HashSet.empty
            for (selector <- set.selectors) {
              for (result <- selector.reference.multiResolve(false)) { //Resolve the name imported by selector
                // Collect shadowed elements
                shadowed += ((selector, result.getElement))
                if (!processor.execute(result.getElement,
                  (state.put(ResolverEnv.nameKey, selector.importedName).
                          put(ImportUsed.key, Set(importsUsed.toSeq: _*) + ImportSelectorUsed(selector))))) {
                  return false
                }
              }
            }

            // There is total import from stable id
            // import a.b.c.{d=>e, f=>_, _}
            if (set.hasWildcard) {
              processor match {
                case bp: BaseProcessor => {
                  val p1 = new BaseProcessor(bp.kinds) {
                    override def getHint[T](hintKey: Key[T]): T = processor.getHint(hintKey)

                    override def handleEvent(event: PsiScopeProcessor.Event, associated: Object) =
                      processor.handleEvent(event, associated)

                    override def execute(element: PsiElement, state: ResolveState): Boolean = {
                      // Register shadowing import selector
                      val elementIsShadowed = shadowed.find(p => elem.equals(p._2))

                      val newState = elementIsShadowed match {
                        case Some((selector, elem)) => {
                          val oldImports = state.get(ImportUsed.key)
                          val newImports = if (oldImports == null) Set[ImportUsed]() else oldImports

                          state.put(ImportUsed.key, Set(newImports.toSeq: _*) + ImportSelectorUsed(selector))
                        }
                        case None => state
                      }

                      if (elementIsShadowed != None) true else processor.execute(element, newState)
                    }
                  }

                  if (!elem.processDeclarations(p1,
                    // In this case import optimizer should check for used selectors
                    state.put(ImportUsed.key, Set(importsUsed.toSeq: _*) + ImportWildcardSelectorUsed(importExpr)),
                    this, place)) return false
                }
                case _ => true
              }

            }
          }
        }
      }
    }

    true
  }
}