package org.jetbrains.plugins.scala.lang.refactoring.util

import java.util.Map

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.{CompositeElement, TreeCopyHandler, TreeElement}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class ScalaChangeUtilSupport extends TreeCopyHandler {
  def encodeInformation(element: TreeElement, original: ASTNode, encodingState: Map[Object, Object]): Unit = {
    if (!element.isInstanceOf[ScalaPsiElement]) return
    if (original.isInstanceOf[CompositeElement]) {
      original.getElementType match {
        case ScalaElementTypes.REFERENCE | ScalaElementTypes.REFERENCE_EXPRESSION | ScalaElementTypes.TYPE_PROJECTION => {
          val res = original.getPsi.asInstanceOf[ScReferenceElement].bind
          res match {
            case Some(ScalaResolveResult(elem: PsiNamedElement, _: ScSubstitutor)) => {
              element.putCopyableUserData(ScalaChangeUtilSupport.REFERENCED_MEMBER_KEY, elem)
            }
            case _ =>
          }
        }
        case _ =>
      }
    }
  }

  def decodeInformation(element: TreeElement, decodingState: Map[Object, Object]): TreeElement = {
    if (!element.isInstanceOf[ScalaPsiElement]) return null
    if (element.isInstanceOf[CompositeElement]) {
      if (element.getElementType == ScalaElementTypes.REFERENCE || element.getElementType == ScalaElementTypes.REFERENCE_EXPRESSION  ||
          element.getElementType == ScalaElementTypes.TYPE_PROJECTION) {
        var ref = SourceTreeToPsiMap.treeElementToPsi(element).asInstanceOf[ScReferenceElement]
        val named: PsiNamedElement = element.getCopyableUserData(ScalaChangeUtilSupport.REFERENCED_MEMBER_KEY)
        if (named != null) {
          element.putCopyableUserData(ScalaChangeUtilSupport.REFERENCED_MEMBER_KEY, null)
          val res = ref.resolve
          if (!element.getManager.areElementsEquivalent(res, named)) {
            try {
              if (ref.qualifier == None) {
                ref = ref.bindToElement(named).asInstanceOf[ScReferenceElement]
              }
            }
            catch {
              case _: IncorrectOperationException =>
            }
            return SourceTreeToPsiMap.psiElementToTree(ref).asInstanceOf[TreeElement]
          } //todo: else
        }
      }
      return element
    }
    return null
  }
}

object ScalaChangeUtilSupport {
  val REFERENCED_MEMBER_KEY: Key[PsiNamedElement] = Key.create("REFERENCED_MEMBER_KEY")
}
