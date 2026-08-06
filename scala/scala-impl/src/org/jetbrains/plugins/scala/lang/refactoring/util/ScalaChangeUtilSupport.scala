package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.{CompositeElement, TreeCopyHandler, TreeElement}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import java.util.Map

class ScalaChangeUtilSupport extends TreeCopyHandler {
  override def encodeInformation(element: TreeElement, original: ASTNode, encodingState: Map[Object, Object]): Unit = {
    if (!element.isInstanceOf[ScalaPsiElement]) return
    if (original.isInstanceOf[CompositeElement]) {
      original.getElementType match {
        case ScalaElementType.REFERENCE | ScalaElementType.REFERENCE_EXPRESSION | ScalaElementType.TYPE_PROJECTION => {
          val res = original.getPsi.asInstanceOf[ScReference].bind()
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

  override def decodeInformation(element: TreeElement, decodingState: Map[Object, Object]): TreeElement = {
    if (!element.isInstanceOf[ScalaPsiElement]) return null
    if (element.isInstanceOf[CompositeElement]) {
      if (element.getElementType == ScalaElementType.REFERENCE || element.getElementType == ScalaElementType.REFERENCE_EXPRESSION ||
        element.getElementType == ScalaElementType.TYPE_PROJECTION) {
        var ref = SourceTreeToPsiMap.treeElementToPsi(element).asInstanceOf[ScReference]
        val named: PsiNamedElement = element.getCopyableUserData(ScalaChangeUtilSupport.REFERENCED_MEMBER_KEY)
        if (named != null) {
          element.putCopyableUserData(ScalaChangeUtilSupport.REFERENCED_MEMBER_KEY, null)
          val res = ref.resolve
          if (!element.getManager.areElementsEquivalent(res, named)) {
            try {
              if (ref.qualifier == None) {
                ref = ref.bindToElement(named).asInstanceOf[ScReference]
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
