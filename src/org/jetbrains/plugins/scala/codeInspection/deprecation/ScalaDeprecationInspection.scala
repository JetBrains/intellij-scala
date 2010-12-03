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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelector, ScImportExpr}
import com.intellij.codeInspection._
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}

/**
 * User: Alexander Podkhalyuzin
 * Date: 13.04.2010
 */

class ScalaDeprecationInspection extends LocalInspectionTool {
  /*override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val res = new ArrayBuffer[ProblemDescriptor]
    def checkDeprecated(refElement: PsiElement, elementToHighlight: PsiElement, name: String): Unit = {
      if (refElement == null) return
      if (!refElement.isInstanceOf[PsiNamedElement]) return
      val context = ScalaPsiUtil.nameContext(refElement.asInstanceOf[PsiNamedElement])
      context match {
        case doc: PsiDocCommentOwner => {
          doc match {
            case _: ScPrimaryConstructor =>
            case f: PsiMethod if f.isConstructor =>
            case _ => if (!doc.isDeprecated) return
          }
          if (!doc.isDeprecated && !doc.getContainingClass.isDeprecated) return
        }
        case _ => return
      }
      val description: String = "Symbol " + name + " is deprecated"
      res += manager.createProblemDescriptor(elementToHighlight, description, true, ProblemHighlightType.LIKE_DEPRECATED)
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
  }*/

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    def checkDeprecated(refElement: PsiElement, elementToHighlight: PsiElement, name: String): Unit = {
      if (refElement == null) return
      if (!refElement.isInstanceOf[PsiNamedElement]) return
      val context = ScalaPsiUtil.nameContext(refElement.asInstanceOf[PsiNamedElement])
      context match {
        case doc: PsiDocCommentOwner => {
          doc match {
            case _: ScPrimaryConstructor =>
            case f: PsiMethod if f.isConstructor =>
            case _ => if (!doc.isDeprecated) return
          }
          if (!doc.isDeprecated && !doc.getContainingClass.isDeprecated) return
        }
        case _ => return
      }
      val description: String = "Symbol " + name + " is deprecated"
      holder.registerProblem(holder.getManager.createProblemDescriptor(elementToHighlight, description, true,
        ProblemHighlightType.LIKE_DEPRECATED))
    }

    new ScalaElementVisitor {
      override def visitFunction(fun: ScFunction): Unit = {
        //todo: check super method is deprecated
      }

      override def visitReference(ref: ScReferenceElement): Unit = {
        checkDeprecated(ref.resolve, ref.nameId, ref.refName)
      }
    }
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

  override def getStaticDescription: String = "This inspection reports where deprecated code is used in the specified inspection scope."
}