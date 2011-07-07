package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.debugger.engine.evaluation.{TextWithImports, CodeFragmentFactory}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCodeFragmentFactory extends CodeFragmentFactory {
  def createCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    //val debuggerContext = DebuggerManager.getInstance(project).asInstanceOf[DebuggerManagerEx].getContext
    //todo: val visibleVariables = debuggerContext.getFrameProxy.visibleVariables()
    //on buildserver visibleVariables is not a member of FrameProxy.
    val contextClass: PsiClass = ScalaPsiUtil.getContextOfType(context, false, classOf[PsiClass]) match {
      case null => null
      case clazz: PsiClass => clazz
    }
    //java text creating


    val result: String = item.getText
    val factory: PsiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory
    val jcf: JavaCodeFragment = factory.createCodeBlockCodeFragment(result, null, true)
    if (contextClass != null) {
      jcf.setThisType(factory.createType(contextClass))
    }
    jcf
  }

  def createPresentationCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    val result: ScalaCodeFragment = new ScalaCodeFragment(project, item.getText)
    result.setContext(context, null)
    result
  }

  def isContextAccepted(contextElement: PsiElement): Boolean = {
    contextElement.getLanguage == ScalaFileType.SCALA_LANGUAGE
  }

  def getFileType: LanguageFileType = ScalaFileType.SCALA_FILE_TYPE
}