package org.jetbrains.plugins.scala.codeInspection.internal

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiClass, PsiElement, PsiElementVisitor, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable


/**
  * @author Alefas
  * @since 02.04.12
  */

class ScalaWrongMethodsUsageInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "ScalaWrongMethodsUsage"

  override def getGroupDisplayName: String = "Internal"

  override def getGroupPath: Array[String] = Array("Scala", "Internal")

  override def getDefaultLevel: HighlightDisplayLevel = HighlightDisplayLevel.WARNING

  override def getDisplayName: String = "Wrong method usage"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.isInstanceOf[ScalaFile]) return new PsiElementVisitor {}

    new ScalaElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        if (!map.contains(ref.refName)) return
        val psiManager = ScalaPsiManager.instance(holder.getProject)
        val scalaPsiElemClazz = psiManager
          .getCachedClass(ref.getResolveScope, "org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement")
          .getOrElse(return)
        val resolved = ref
          .bind()
          .collect( { case ScalaResolveResult(m: PsiMethod, _) => (m, Option(m.getContainingClass), map.get(m.name)) })
        resolved match {
          case Some((method, Some(containingClass), Some(mapped))) if mapped.nonEmpty =>
            val found = mapped.exists {
              case clazz =>
                val cachedClass = psiManager.getCachedClass(method.getResolveScope, clazz)
                val hasScalaPsiUpper = ref.smartQualifier.map(_.getType()) match {
                  case Some(Success(ScThisType(thisclass), _)) =>
                    !psiManager.cachedDeepIsInheritor(thisclass, scalaPsiElemClazz)
                  case Some(Success(ScDesignatorType(elem: PsiClass), _)) =>
                    !psiManager.cachedDeepIsInheritor(elem, scalaPsiElemClazz)
                  case _ =>
                    false
                }
                val isPsiInheritor = psiManager.cachedDeepIsInheritor(cachedClass.get, containingClass)

                cachedClass.isDefined && isPsiInheritor && hasScalaPsiUpper
            }

            if (found)
              holder.registerProblem(ref.nameId, "Don't use this method, use appropriate method implemented for Scala, or use " +
                "\"for Java only\" text in bounded doc comment owner ScalaDoc",
                ProblemHighlightType.LIKE_DEPRECATED)
        }
      }
    }

//    new ScalaElementVisitor {
//      override def visitReferenceExpression(ref: ScReferenceExpression) {
//        val resolve = ref.resolve()
//        resolve match {
//          case m: PsiMethod =>
//            map.get(m.name) match {
//              case Some(classes) =>
//                val containingClass = m.containingClass
//                classes.find {
//                  case clazz =>
//                    val instance = ScalaPsiManager.instance(holder.getProject)
//                    val cachedClass = instance.getCachedClass(m.getResolveScope, clazz).orNull
//                    if (cachedClass != null && containingClass != null) {
//                      if (cachedClass == containingClass || instance.cachedDeepIsInheritor(cachedClass, containingClass)) {
//                        true
//                      } else false
//                    } else false
//                } match {
//                  case Some(clazz) =>
//                    var parent: PsiElement = ref.getParent
//                    while (parent != null) {
//                      parent match {
//                        case f: ScDocCommentOwner =>
//                          f.docComment match {
//                            case Some(d) => if (d.getText.contains("for Java only")) return
//                            case _ =>
//                          }
//                        case _ =>
//                      }
//                      parent = parent.getParent
//                    }
//                    holder.registerProblem(ref.nameId, "Don't use this method, use appropriate method implemented for Scala, or use " +
//                      "\"for Java only\" text in bounded doc comment owner ScalaDoc",
//                      ProblemHighlightType.LIKE_DEPRECATED)
//                  case _ =>
//                }
//              case _ =>
//            }
//          case _ =>
//        }
//      }
//    }


  }

  private lazy val map = scala.collection.immutable.HashMap[String, Seq[String]](
    ("getContainingClass", Seq("com.intellij.psi.PsiMember")),
    ("getQualifiedName", Seq("com.intellij.psi.PsiClass")),
    ("getName", Seq("com.intellij.navigation.NavigationItem", "com.intellij.psi.PsiNamedElement")),
    ("getClasses", Seq("com.intellij.psi.PsiClassOwner")),
    ("getClassNames", Seq("com.intellij.psi.PsiClassOwnerEx")),
    ("getClassNames", Seq("com.intellij.psi.PsiClassOwnerEx")),
    ("hasModifierProperty", Seq("com.intellij.psi.PsiModifierListOwner"))
  )
}
