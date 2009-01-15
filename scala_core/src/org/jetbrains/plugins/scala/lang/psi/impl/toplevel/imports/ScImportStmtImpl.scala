package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import _root_.org.jetbrains.plugins.scala.lang.resolve.{ResolverEnv, BaseProcessor}
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import _root_.scala.collection.mutable.HashSet

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScImportStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportStmt {
  override def toString: String = "ScImportStatement"

  import scope._

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (e <- importExprs) {
      if (e == lastParent) return true
      val elems = e.reference match {
        case Some(ref) => ref.multiResolve(false).map{_.getElement}
        case _ => Seq.empty
      }

      for (elem <- elems) {
        e.selectorSet match {
          case None =>
            if (e.singleWildcard) {
              if (!elem.processDeclarations(processor, state, this, place)) return false
            } else {
              if (!processor.execute(elem, state)) return false
            }
          case Some(set) => {
            val shadowed: HashSet[PsiElement] = HashSet.empty
            for (selector <- set.selectors) {
              for (result <- selector.reference.multiResolve(false)) {
                shadowed += result.getElement
                if (!processor.execute(result.getElement, state.put(ResolverEnv.nameKey, selector.importedName))) return false
              }
            }
            if (set.hasWildcard) {
              processor match {
                case bp: BaseProcessor => {
                  val p1 = new BaseProcessor(bp.kinds) {
                    override def getHint[T](hintClass: Class[T]): T = processor.getHint(hintClass)

                    override def handleEvent(event: PsiScopeProcessor.Event, associated: Object) =
                      processor.handleEvent(event, associated)

                    override def execute(element: PsiElement, state: ResolveState): Boolean = {
                      if (shadowed.contains(element)) true else processor.execute(element, state)
                    }
                  }
                  if (!elem.processDeclarations(p1, state, this, place)) return false
                }
                case _  => true
              }

            }
          }
        }
      }
    }

    true
  }
}