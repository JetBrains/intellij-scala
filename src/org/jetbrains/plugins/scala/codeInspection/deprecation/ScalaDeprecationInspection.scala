package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import com.intellij.psi._
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import infos.MethodCandidateInfo
import com.intellij.psi.PsiClassType.ClassResolveResult
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil
import com.intellij.codeInsight.daemon.JavaErrorMessages
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelector, ScImportExpr}
import com.intellij.codeInspection._
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 13.04.2010
 */

class ScalaDeprecationInspection extends LocalInspectionTool {
  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val res = new ArrayBuffer[ProblemDescriptor]
    def checkDeprecated(refElement: PsiElement, elementToHighlight: PsiElement, name: String): Unit = {
      if (refElement == null) return
      if (!refElement.isInstanceOf[PsiNamedElement]) return
      val context = ScalaPsiUtil.nameContext(refElement.asInstanceOf[PsiNamedElement])
      if (!context.isInstanceOf[PsiDocCommentOwner]) return
      if (!(context.asInstanceOf[PsiDocCommentOwner]).isDeprecated) return
      var description: String = "Symbol " + name + " is deprecated"
      res += manager.createProblemDescriptor(elementToHighlight, description, false, ProblemHighlightType.LIKE_DEPRECATED)
    }
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitFunction(fun: ScFunction): Unit = {
        //todo: check super method is deprecated
      }

      override def visitReference(ref: ScReferenceElement): Unit = {
        checkDeprecated(ref.resolve, ref.nameId, ref.refName)
      }
    }
    file.accept(visitor)
    res.toArray
  }

  def getDisplayName: String = {
    return "Scala Deprecation"
  }

  def getGroupDisplayName: String = {
    return InspectionsUtil.SCALA
  }

  def getShortName: String = {
    return "Scala Deprecation"
  }

  override def getID: String = {
    return "ScalaDeprecation"
  }

  override def isEnabledByDefault: Boolean = {
    return true
  }
}