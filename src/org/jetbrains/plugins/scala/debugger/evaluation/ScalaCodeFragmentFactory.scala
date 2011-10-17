package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.debugger.engine.evaluation.{TextWithImports, CodeFragmentFactory}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCodeFragmentFactory extends CodeFragmentFactory {
  def createCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    val result: ScalaCodeFragment = new ScalaCodeFragment(project, item.getText)
    result.setContext(context, null)
    result
  }

  def createPresentationCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    createCodeFragment(item, context, project)
  }

  def isContextAccepted(contextElement: PsiElement): Boolean = {
    if (contextElement.isInstanceOf[PsiCodeBlock]) {
      return contextElement.getContext != null && contextElement.getContext.getContext != null &&
        contextElement.getContext.getContext.getLanguage == ScalaFileType.SCALA_LANGUAGE
    }
    if (contextElement == null) return false
    contextElement.getLanguage == ScalaFileType.SCALA_LANGUAGE
  }

  def getFileType: LanguageFileType = ScalaFileType.SCALA_FILE_TYPE

  def getEvaluatorBuilder: EvaluatorBuilder = ScalaEvaluatorBuilder
}