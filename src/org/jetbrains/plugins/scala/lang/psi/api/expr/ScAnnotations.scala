package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScTypeExt}

/**
 * @author Alexander Podkhalyuzin
 *                         Date: 07.03.2008
 */

trait ScAnnotations extends ScalaPsiElement with PsiReferenceList {
  def getReferenceElements = Array[PsiJavaCodeReferenceElement]()


  def foldFuns(initial: Any)(fail: Any)(l: List[PartialFunction[Any, _]]): Any = l match {
    case h :: t => if (h.isDefinedAt(initial)) foldFuns(h(initial))(fail)(t) else fail
    case Nil => initial
  }

  // todo rewrite via continuations
  private def getExceptionTypes: Array[PsiClassType] = {
    val annotations = getAnnotations
    annotations.map(extractExceptionType).filter(_ != null)
  }

  private def extractExceptionType(a: ScAnnotation): PsiClassType = {
    val constr = a.annotationExpr.constr
    constr.typeElement match {
      case te: ScSimpleTypeElement =>
        te.reference match {
          case Some(ref) =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) if r.getActualElement.isInstanceOf[PsiClass] &&
                  r.getActualElement.asInstanceOf[PsiClass].qualifiedName == "scala.throws" =>
                constr.args match {
                  case Some(args) if args.exprs.length == 1 =>
                    args.exprs(0).getType(TypingContext.empty) match {
                      case Success(ScParameterizedType(tp, arg), _) if arg.length == 1 =>
                        tp.extractClass(getProject) match {
                          case Some(clazz) if clazz.qualifiedName == "java.lang.Class" =>
                            arg.head.extractClass(getProject) match {
                              case Some(p) =>
                                JavaPsiFacade.getInstance(getProject).getElementFactory.
                                  createTypeByFQClassName(p.qualifiedName, GlobalSearchScope.allScope(getProject))
                              case _ => null
                            }
                          case _ => null
                        }
                      case _ => null
                    }
                  case _ => null
                }
              case _ => null
            }
          case _ => null
        }
      case _ => null
    }
  }

  def getReferencedTypes = getExceptionTypes

  //todo return appropriate roles
  def getRole = PsiReferenceList.Role.THROWS_LIST

  def getAnnotations: Array[ScAnnotation]
}