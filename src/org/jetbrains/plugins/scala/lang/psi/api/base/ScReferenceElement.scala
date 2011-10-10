package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import _root_.org.jetbrains.plugins.scala.lang.resolve._
import _root_.scala.collection.Set
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import com.intellij.openapi.util.TextRange
import refactoring.util.ScalaNamesUtil
import statements.{ScTypeAliasDefinition, ScFunction}
import toplevel.typedef._
import psi.types._
import psi.impl.ScalaPsiElementFactory
import statements.params.ScTypeParam
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScReferenceElement extends ScalaPsiElement with ResolvableReferenceElement {
  override def getReference = this

  def nameId: PsiElement

  def refName: String = {
    val text: String = nameId.getText
    if (text.charAt(0) == '`' && text.length > 1) text.substring(1, text.length - 1)
    else text
  }

  def getElement = this

  def getRangeInElement: TextRange =
    new TextRange(nameId.getTextRange.getStartOffset - getTextRange.getStartOffset, getTextLength)

  def getCanonicalText: String = null

  def isSoft: Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    if (!ScalaNamesUtil.isIdentifier(newElementName)) return this
    val isQuoted = nameId.getText.startsWith("`")
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id,
      ScalaPsiElementFactory.createIdentifier(if (isQuoted) "`" + newElementName + "`" else newElementName, getManager))
    this
  }

  def isReferenceTo(element: PsiElement): Boolean = {
    val iterator = multiResolve(false).iterator
    while (iterator.hasNext) {
      val resolved = iterator.next()
      if (isReferenceTo(element, resolved.getElement)) return true
    }
    false
  }

  def isReferenceTo(element: PsiElement, resolved: PsiElement): Boolean = {
    if (ScEquivalenceUtil.smartEquivalence(resolved, element)) return true
    element match {
      case td: ScTypeDefinition if td.getName == refName => {
        resolved match {
          case method: PsiMethod if method.isConstructor => {
            if (td == method.getContainingClass) return true
          }
          case method: ScFunction if Set("apply", "unapply", "unapplySeq").contains(method.getName) => {
            var break = false
            val methods = td.allMethods
            for (n <- methods if !break) {
              if (n.method.getName == method.getName) {
                val methodContainingClass: ScTemplateDefinition = method.getContainingClass
                val nodeMethodContainingClass: PsiClass = n.method.getContainingClass
                val classesEquiv: Boolean = {
                  val equal = methodContainingClass == nodeMethodContainingClass
                  lazy val syntheticEquiv = td match {
                    // TODO investigate why we need this.
                    //      Trigger the CreateCaseClauses intention on `(null: Either[Int, String) match {}`, and without
                    //      this we get unneeded imports: `import scala.{Left, Right}`
                    //      Seems We end up with multiple instances of the synthetic companion object for a single case class
                    case obj: ScObject if obj.isSyntheticObject && obj.getQualifiedName == nodeMethodContainingClass.getQualifiedName => true
                    case _ => false
                  }
                  equal || syntheticEquiv
                }
                if (classesEquiv)
                  break = true
              }
            }

            if (!break && method.getText.contains("throw new Error()") && td.isInstanceOf[ScClass] &&
              td.asInstanceOf[ScClass].isCase) {
              ScalaPsiUtil.getCompanionModule(td) match {
                case Some(td) => return isReferenceTo(td)
                case _ =>
              }
            }
            if (break) return true
          }
          case obj: ScObject if obj.isSyntheticObject => {
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(td) if td == obj => return true
              case _ =>
            }
          }
          case _ =>
        }
      }
      case _ =>
    }
    isIndirectReferenceTo(resolved, element)
  }

  /**
   * Is `resolved` (the resolved target of this reference) itself a reference to `element`, by way of a type alias defined in a object, such as:
   *
   * object Predef { type Throwable = java.lang.Throwable }
   *
   * @see http://youtrack.jetbrains.net/issue/SCL-3132
   */
  private def isIndirectReferenceTo(resolved: PsiElement, element: PsiElement): Boolean = {
    def isDefinedInObject(memb: ScMember) = memb.getContainingClass.isInstanceOf[ScObject]
    (resolved, element) match {
      case (_, obj: ScObject) =>
        // TODO indirect references via vals, e.g. `package object scala { val List = scala.collection.immutable.List }` ?
      case (typeAlias: ScTypeAliasDefinition, cls: PsiClass) if isDefinedInObject(typeAlias) =>
        if (cls.getTypeParameters.length != typeAlias.typeParameters.length) {
          return false
        } else if (cls.hasTypeParameters) {
          val typeParamsAreAppliedInOrderToCorrectClass = typeAlias.aliasedType.getOrAny match {
            case pte: ScParameterizedType =>
              val refersToClass = Equivalence.equiv(pte.designator, ScType.designator(cls))
              val typeParamsAppliedInOrder = (pte.typeArgs corresponds typeAlias.typeParameters) {
                case (tpt: ScTypeParameterType, tp) if tpt.param == tp => true
                case _ => false
              }
              refersToClass && typeParamsAppliedInOrder
            case _ => false
          }
          val varianceAndBoundsMatch = cls match {
            case sc: ScClass =>
              (typeAlias.typeParameters corresponds sc.typeParameters) {
                case (tp1, tp2) => tp1.variance == tp2.variance && tp1.upperBound == tp2.upperBound && tp1.lowerBound == tp2.lowerBound &&
                        tp1.contextBound.isEmpty && tp2.contextBound.isEmpty && tp1.viewBound.isEmpty && tp2.viewBound.isEmpty
              }
            case _ => // Java class
              (typeAlias.typeParameters corresponds cls.getTypeParameters) {
                case (tp1, tp2) => tp1.variance == ScTypeParam.Invariant && tp1.upperTypeElement.isEmpty && tp2.getExtendsListTypes.isEmpty &&
                        tp1.lowerTypeElement.isEmpty && tp1.contextBound.isEmpty && tp1.viewBound.isEmpty
              }
          }
          typeParamsAreAppliedInOrderToCorrectClass && varianceAndBoundsMatch
        } else {
          val clsType = ScType.designator(cls)
          typeAlias.typeParameters.isEmpty && Equivalence.equiv(typeAlias.aliasedType.getOrElse(return false), clsType)
        }
      case _ =>
    }
    val originalElement = element.getOriginalElement
    if (originalElement != element) isReferenceTo(originalElement, resolved)
    else false
  }

  def qualifier: Option[ScalaPsiElement]

  //provides the set of possible namespace alternatives based on syntactic position
  def getKinds(incomplete: Boolean, completion: Boolean = false): Set[ResolveTargets.Value]

  def getVariants(implicits: Boolean, filterNotNamedVariants: Boolean): Array[Object] = getVariants()

  def getSameNameVariants: Array[ResolveResult]

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitReference(this)
  }
}