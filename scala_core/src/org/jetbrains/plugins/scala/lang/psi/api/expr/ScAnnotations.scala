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
 *                        Date: 07.03.2008
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
    annotations.map(extractExceptionType _).filter(_ != null)
  }

  private def extractExceptionType(a: ScAnnotation) = {
    val constr = a.annotationExpr.constr
    val result = foldFuns(constr.typeElement.reference.map(_.bind))(null)(List(
      {case Some(Some(res: ScalaResolveResult)) => res.getElement},
      {case c: PsiClass if c.getQualifiedName == "scala.throws" => constr.args},
      {case args: ScArgumentExprList if args != null => args.exprs},
      {case Seq(gc@(_: ScGenericCall)) => (gc.referencedExpr, gc.typeArgs.typeArgs)},
      {case (ref: ScReferenceExpression, ta) => (ref.bind.map(_.getElement), ta)},
      {case (Some(m: PsiMethod), ta) if m.getName == "classOf" => ta},
      {case Seq(s: ScSimpleTypeElement) => s.reference.map(_.bind)},
      {case Some(Some(res1: ScalaResolveResult)) => res1.getElement},
      {case p: PsiClass => JavaPsiFacade.getInstance(getProject).getElementFactory.createTypeByFQClassName(p.getQualifiedName, GlobalSearchScope.allScope(getProject))}
      ))

    result match {
      case p: PsiClassType => p
      case _ => null
    }
  }

  def getReferencedTypes = getExceptionTypes

  //todo return appropriate roles
  def getRole = PsiReferenceList.Role.THROWS_LIST

  def getAnnotations: Array[ScAnnotation] = findChildrenByClass(classOf[ScAnnotation])
}