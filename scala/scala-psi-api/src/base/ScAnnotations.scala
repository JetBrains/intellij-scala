package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType

trait ScAnnotations extends ScalaPsiElement with PsiReferenceList {

  def getAnnotations: Array[ScAnnotation]

  override def getReferenceElements: Array[PsiJavaCodeReferenceElement] = Array[PsiJavaCodeReferenceElement]()

  def foldFuns(initial: Any)(fail: Any)(l: List[PartialFunction[Any, _]]): Any = l match {
    case h :: t => if (h.isDefinedAt(initial)) foldFuns(h(initial))(fail)(t) else fail
    case Nil => initial
  }

  //todo return appropriate roles
  override def getRole = PsiReferenceList.Role.THROWS_LIST

  override def getReferencedTypes: Array[PsiClassType] = getExceptionTypes

  // todo rewrite via continuations
  private def getExceptionTypes: Array[PsiClassType] = {
    val annotations = getAnnotations
    annotations.map(extractExceptionType).filter(_ != null)
  }

  private def extractExceptionType(a: ScAnnotation): PsiClassType = {
    val constr = a.annotationExpr.constructorInvocation
    constr.typeElement match {
      case te: ScSimpleTypeElement =>
        te.reference match {
          case Some(ref) =>
            ref.bind() match {
              case Some(r) if r.getActualElement.isInstanceOf[PsiClass] &&
                  r.getActualElement.asInstanceOf[PsiClass].qualifiedName == "scala.throws" =>
                constr.args match {
                  case Some(args) if args.exprs.length == 1 =>
                    args.exprs(0).`type`() match {
                      case Right(ParameterizedType(tp, arg)) if arg.length == 1 =>
                        tp.extractClass match {
                          case Some(clazz) if clazz.qualifiedName == "java.lang.Class" =>
                            arg.head.extractClass match {
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
}
