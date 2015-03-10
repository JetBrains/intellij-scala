package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.changeSignature.JavaParameterInfo
import com.intellij.refactoring.util.CanonicalTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

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
                         var keywordsAndAnnotations: String = "")
        extends JavaParameterInfo {

  def this(p: ScParameter) {
    this(p.name, p.index, p.getType(TypingContext.empty).getOrAny, p.getProject, p.isRepeatedParameter, p.isCallByNameParameter,
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

  private def psiType: PsiType = {
    if (scType == null) return null

    val allScope = GlobalSearchScope.allScope(project)
    if (isByName) {
      val functionType = ScFunctionType(scType, Seq())(project, allScope)
      ScType.toPsi(functionType, project, allScope)
    }
    else if (isRepeatedParameter) {
      val seqType = ScDesignatorType.fromClassFqn("scala.collection.Seq", project, allScope)
      ScType.toPsi(ScParameterizedType(seqType, Seq(scType)), project, allScope)
    }
    else ScType.toPsi(scType, project, allScope)
  }

  override def createType(context: PsiElement, manager: PsiManager): PsiType = psiType

  override def getValue(expr: PsiCallExpression): PsiExpression = {
    if (defaultForJava.isEmpty) return null
    val expression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(defaultForJava, expr)
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression).asInstanceOf[PsiExpression]
  }

  override def getTypeWrapper: CanonicalTypes.Type = {
    if (scType != null) CanonicalTypes.createTypeWrapper(psiType) else null
  }

  override def getTypeText: String =
    if (scType != null) getTypeWrapper.getTypeText else null

  def typeText = {
    val baseText = Option(scType).fold("")(_.presentableText)
    if (isRepeatedParameter) baseText + "*"
    else if (isByName) " => " + baseText
    else baseText
  }
}

object ScalaParameterInfo {
  def apply(p: ScParameter) = new ScalaParameterInfo(p)

  def apply(project: Project) = new ScalaParameterInfo("", -1, null, project, false, false)

  def keywordsAndAnnotations(p: ScParameter) = {
    val nameId = p.nameId
    val elems = p.children.takeWhile(_ != nameId)
    elems.map(_.getText).mkString
  }
}
