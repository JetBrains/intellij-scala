package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.evaluation.{CodeFragmentFactory, TextWithImports}
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.JavaDummyHolder
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.{JavaCodeFragment, JavaPsiFacade, PsiElement}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.expr.ScReferenceExpression
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.typedef.ScObject
import lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.02.2009
 */

class ScalaCodeFragmentFactory extends CodeFragmentFactory {
  def getFileType: LanguageFileType = ScalaFileType.SCALA_FILE_TYPE


  def isContextAccepted(context: PsiElement): Boolean = context != null && context.getLanguage == ScalaFileType.SCALA_LANGUAGE
          /*|| (context.getParent.isInstanceOf[JavaDummyHolder]
          && context.getParent.asInstanceOf[JavaDummyHolder].getContext.getContainingFile.isInstanceOf[ScalaFile]))*/

  def createCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    val text = item.getText
    val imports = item.getImports
    val toEval = ScalaPsiElementFactory.createScalaFile(text, context.getManager)
    val elementFactory = JavaPsiFacade.getInstance(toEval.getProject).getElementFactory
    val javaText = new StringBuilder("")
    def convertToJava(element: PsiElement) {
      element match {
        case _: ScalaFile => {
          for (child <- element.getNode.getChildren(null); psi = child.getPsi) convertToJava(psi)
        }
        case e if e.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR => javaText.append(";")
        case ref: ScReferenceExpression if ref.resolve != null => {
          ref.resolve match {
            case obj: ScObject => javaText.append("(new " + obj.getQualifiedName + "$())")
            case _ => javaText.append(element.getText)
          }
        }
        case _ => javaText.append(element.getText)
      }
    }
    convertToJava(toEval)
    val result = elementFactory.createCodeBlockCodeFragment(javaText.toString, null, true)
    result
  }

  def createPresentationCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    val result = new ScalaCodeFragment(project, item.getText)
    result.setContext(context)
    result
  }
}