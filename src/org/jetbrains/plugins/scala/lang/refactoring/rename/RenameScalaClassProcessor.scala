package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.refactoring.rename.RenameJavaClassProcessor
import com.intellij.psi.PsiElement
import java.util.Map
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import com.intellij.usageView.UsageInfo
import com.intellij.refactoring.listeners.RefactoringElementListener
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocTagValue, ScDocComment}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class RenameScalaClassProcessor extends RenameJavaClassProcessor {
  override def canProcessElement(element: PsiElement): Boolean = {
    element.isInstanceOf[ScTypeDefinition] || element.isInstanceOf[ScTypeParam]
  }

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: Map[PsiElement, String]) {
    element match {
      case td: ScTypeDefinition => {
        ScalaPsiUtil.getCompanionModule(td) match {
          case Some(td) => allRenames.put(td, newName)
          case _ =>
        }
        val file = td.getContainingFile
        if (file != null && file.getName == td.getName + ".scala") {
          allRenames.put(file, newName + ".scala")
        }
      }
      case docTagParam: ScTypeParam =>
        docTagParam.owner match {
          case commentOwner: ScDocCommentOwner =>
            commentOwner.getDocComment match {
              case comment: ScDocComment =>
                comment.findTagsByName(MyScaladocParsing.TYPE_PARAM_TAG).foreach {
                  b => if (b.getValueElement != null && b.getValueElement.getText == docTagParam.getName)
                    allRenames.put(b.getValueElement, newName)
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
  }
}