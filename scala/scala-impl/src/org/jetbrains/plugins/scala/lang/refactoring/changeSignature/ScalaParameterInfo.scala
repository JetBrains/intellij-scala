package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.refactoring.changeSignature.JavaParameterInfo
import com.intellij.refactoring.util.CanonicalTypes
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, JavaArrayType, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring._

import scala.beans.{BeanProperty, BooleanBeanProperty}

/**
 * Nikolay.Tropin
 * 2014-08-10
 */
class ScalaParameterInfo(@BeanProperty var name: String,
                         @BeanProperty val oldIndex: Int,
                         var scType: ScType,
                         val project: Project,
                         var isRepeatedParameter: Boolean,
                         var isByName: Boolean,
                         @BeanProperty var defaultValue: String = "",
                         var keywordsAndAnnotations: String = "",
                         val isIntroducedParameter: Boolean = false)
        extends JavaParameterInfo {

  def this(p: ScParameter) {
    this(p.name, p.index, p.`type`().getOrAny, p.getProject, p.isRepeatedParameter, p.isCallByNameParameter,
      keywordsAndAnnotations = ScalaParameterInfo.keywordsAndAnnotations(p))
  }

  var defaultForJava = defaultValue

  @BooleanBeanProperty
  var useAnySingleVariable: Boolean = false

  val wasArrayType: Boolean = scType match {
    case JavaArrayType(_) => true
    case _ => false
  }

  val isVarargType = false //overriders in java of method with repeated parameters are not varargs

  protected def psiType: PsiType = {
    if (scType == null) return null

    implicit val elementScope = ElementScope(project)

    val resultType = if (isByName) {
      val functionType = FunctionType(scType, Seq())
      functionType
    }
    else if (isRepeatedParameter) {
      val seqType = ScalaPsiManager.instance(project).getCachedClass(elementScope.scope, "scala.collection.Seq")
        .map(ScalaType.designator(_))
        .getOrElse(Nothing)
      ScParameterizedType(seqType, Seq(scType))
    }
    else scType

    resultType.toPsiType
  }

  override def createType(context: PsiElement, manager: PsiManager): PsiType = psiType

  override def getValue(expr: PsiCallExpression): PsiExpression = {
    if (defaultForJava.isEmpty) return null
    val defaultText =
      if (defaultForJava.contains("$default$")) {
        val qual = expr match {
          case mc: PsiMethodCallExpression =>
            mc.getMethodExpression.getQualifierExpression match {
              case _: PsiSuperExpression => ""
              case null => ""
              case q => q.getText + "."
            }
          case _ => ""
        }
        qual + defaultForJava
      } else defaultForJava

    val expression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(defaultText, expr)
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression).asInstanceOf[PsiExpression]
  }

  override def getTypeWrapper: CanonicalTypes.Type = {
    if (scType != null) CanonicalTypes.createTypeWrapper(psiType) else null
  }

  override def getTypeText: String =
    if (scType != null) getTypeWrapper.getTypeText else null

  def typeText(implicit context: TypePresentationContext): String = {
    val baseText = Option(scType).fold("")(_.codeText)
    if (isRepeatedParameter) baseText + "*"
    else if (isByName) " => " + baseText
    else baseText
  }
}

object ScalaParameterInfo {
  def apply(p: ScParameter) = new ScalaParameterInfo(p)

  def apply(project: Project) = new ScalaParameterInfo("", -1, null, project, false, false)

  def keywordsAndAnnotations(p: ScParameter): String = {
    val nameId = p.nameId
    val elems = p.children.takeWhile(_ != nameId)
    elems.map(_.getText).mkString
  }
  
  def allForMethod(methodLike: ScMethodLike): Seq[Seq[ScalaParameterInfo]] = {
    def infos(clause: ScParameterClause): Seq[ScalaParameterInfo] = clause.parameters.map(new ScalaParameterInfo(_))
    methodLike.parameterList.clauses.map(infos)
  }
}
