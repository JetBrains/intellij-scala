package org.jetbrains.plugins.scala.lang.psi.api.expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import base.ScStableCodeReferenceElement
import base.types.ScSimpleTypeElement
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
 * @author Alexander Podkhalyuzin
 *                Date: 07.03.2008
 */

trait ScAnnotations extends ScalaPsiElement with PsiReferenceList {
  def getReferenceElements = Array[PsiJavaCodeReferenceElement]()


  // todo rewrite via continuations
  private def getExceptionTypes: Array[PsiClassType] = {
    val annotations = getAnnotations
    annotations.map(extractExceptionType _).filter(_ != null)
  }

  private def extractExceptionType(a: ScAnnotation) = {
    val constr = a.annotationExpr.constr
    try {
      constr.typeElement.reference.map(_.bind) match {
        case Some(Some(res: ScalaResolveResult)) => res.getElement match {
          case c: PsiClass if c.getQualifiedName == "scala.throws" => constr.args match {
            case args: ScArgumentExprList if args != null => args.exprs match {
              case Seq(gc@(_: ScGenericCall)) => gc.referencedExpr match {
                case ref: ScReferenceExpression => ref.bind.map(_.getElement) match {
                  case Some(m: PsiMethod) if m.getName == "classOf" => gc.typeArgs.typeArgs match {
                    case Seq(s: ScSimpleTypeElement) => s.reference.map(_.bind) match {
                      case Some(Some(res1: ScalaResolveResult)) => res1.getElement match {
                        case p: PsiClass => JavaPsiFacade.getInstance(getProject).getElementFactory.createTypeByFQClassName(p.getQualifiedName, GlobalSearchScope.allScope(getProject))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    catch {
      case e: MatchError => null
    }
  }

  def getReferencedTypes = getExceptionTypes

  //todo return appropriate roles
  def getRole = PsiReferenceList.Role.THROWS_LIST

  def getAnnotations: Array[ScAnnotation] = findChildrenByClass(classOf[ScAnnotation])
}