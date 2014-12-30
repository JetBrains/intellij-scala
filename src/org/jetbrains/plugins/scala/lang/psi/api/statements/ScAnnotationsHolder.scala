package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi._
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScAnnotations}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

trait ScAnnotationsHolder extends ScalaPsiElement with PsiAnnotationOwner {
  def annotations: Seq[ScAnnotation] = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case st: StubBasedPsiElement[_] if st.getStub != null =>
        st.getStub.asInstanceOf[StubElement[_ <: PsiElement]] // !!! Appeasing an unexplained compile error
      case file: PsiFileImpl if file.getStub != null => file.getStub
      case _ => null
    }
    if (stub != null) {
      val annots: Array[ScAnnotations] =
        stub.getChildrenByType(TokenSet.create(ScalaElementTypes.ANNOTATIONS), JavaArrayFactoryUtil.ScAnnotationsFactory)
      if (annots.length > 0) {
        return annots(0).getAnnotations.toSeq
      } else return Seq.empty
    }
    if (findChildByClassScala(classOf[ScAnnotations]) != null)
      findChildByClassScala(classOf[ScAnnotations]).getAnnotations.toSeq
    else Seq.empty
  }

  def annotationNames: Seq[String] = annotations.map((x: ScAnnotation) => {
    val text: String = x.annotationExpr.constr.typeElement.getText
    text.substring(text.lastIndexOf(".", 0) + 1, text.length)
  })

  def hasAnnotation(clazz: PsiClass): Boolean = hasAnnotation(clazz.qualifiedName) != None

  def hasAnnotation(qualifiedName: String): Option[ScAnnotation] = {
    def acceptType(tp: ScType): Boolean = {
      tp match {
        case ScDesignatorType(clazz: PsiClass) => clazz.qualifiedName == qualifiedName
        case ScParameterizedType(ScDesignatorType(clazz: PsiClass), _) => clazz.qualifiedName == qualifiedName
        case _ =>
          tp.isAliasType match {
            case Some(AliasType(ta: ScTypeAliasDefinition, _, _)) => acceptType(ta.aliasedType(TypingContext.empty).getOrAny)
            case _ => false
          }
      }
    }
    annotations.find(annot => acceptType(annot.typeElement.getType(TypingContext.empty).getOrAny))
  }

  def addAnnotation(qualifiedName: String): PsiAnnotation = {
    val container = findChildByClassScala(classOf[ScAnnotations])

    val element = ScalaPsiElementFactory.createAnAnnotation(qualifiedName, getManager)

    val added = container.add(element).asInstanceOf[PsiAnnotation]
    container.add(ScalaPsiElementFactory.createNewLine(getManager))

    ScalaPsiUtil.adjustTypes(added, true)

    added
  }

  def findAnnotation(qualifiedName: String): PsiAnnotation = {
    hasAnnotation(qualifiedName) match {
      case Some(x) => x
      case None => null
    }
  }

  def getApplicableAnnotations: Array[PsiAnnotation] = getAnnotations //todo: understatnd and fix

  def getAnnotations: Array[PsiAnnotation] = annotations.toArray
}