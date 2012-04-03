package org.jetbrains.plugins.scala.codeInspection.internal

import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import collection.mutable.HashMap
import org.jetbrains.plugins.scala.extensions.{toPsiMemberExt, toPsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import annotation.tailrec
import com.intellij.psi.{PsiElement, PsiMethod, PsiElementVisitor}
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder, LocalInspectionTool}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import com.intellij.codeHighlighting.HighlightDisplayLevel


/**
 * @author Alefas
 * @since 02.04.12
 */

class ScalaWrongMethodsUsageInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean =  true

  override def getID: String = "ScalaWrongMethodsUsage"

  override def getGroupDisplayName: String = "Scala: Internal"

  override def getDefaultLevel: HighlightDisplayLevel = HighlightDisplayLevel.WARNING

  override def getDisplayName: String = "Wrong method usage"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.isInstanceOf[ScalaFile]) return new PsiElementVisitor {}
    new ScalaElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression) {
        val resolve = ref.resolve()
        val map = new HashMap[String, Seq[String]]()
        map += (("getContainingClass", Seq("com.intellij.psi.PsiMember")))
        map += (("getQualifiedName", Seq("com.intellij.psi.PsiClass")))
        map += (("getName", Seq("com.intellij.navigation.NavigationItem", "com.intellij.psi.PsiNamedElement")))
        map += (("getClasses", Seq("com.intellij.psi.PsiClassOwner")))
        resolve match {
          case m: PsiMethod =>
            map.get(m.name) match {
              case Some(classes) =>
                val containingClass = m.containingClass
                classes.find {
                  case clazz =>
                    val instance = ScalaPsiManager.instance(holder.getProject)
                    val cachedClass = instance.getCachedClass(m.getResolveScope, clazz)
                    if (cachedClass != null) {
                      if (cachedClass == containingClass || instance.cachedDeepIsInheritor(cachedClass, containingClass)) {
                        true
                      } else false
                    } else false
                } match {
                  case Some(clazz) =>
                    var parent: PsiElement = ref.getParent
                    while (parent != null) {
                      parent match {
                        case f: ScDocCommentOwner =>
                          f.docComment match {
                            case Some(d) => if (d.getText.contains("for Java only")) return
                            case _ =>
                          }
                        case _ =>
                      }
                      parent = parent.getParent
                    }
                    holder.registerProblem(ref.nameId, "Don't use this method, use appropriate method implemented for Scala, or use " +
                      "\"for Java only\" text in bounded doc comment owner ScalaDoc",
                      ProblemHighlightType.LIKE_DEPRECATED)
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
      }
    }
  }
}
