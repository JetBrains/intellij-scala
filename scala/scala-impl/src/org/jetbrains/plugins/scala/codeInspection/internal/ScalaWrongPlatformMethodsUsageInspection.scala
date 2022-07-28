package org.jetbrains.plugins.scala.codeInspection.internal

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiElementVisitor, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager


/**
 * TODO: move to DevKit module
 */
class ScalaWrongPlatformMethodsUsageInspection extends LocalInspectionTool {

  import ScalaWrongPlatformMethodsUsageInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.isInstanceOf[ScalaFile]) return PsiElementVisitor.EMPTY_VISITOR

    new ScalaElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        val resolve = ref.resolve()

        resolve match {
          case m: PsiMethod =>
            methodNameToClasses.get(m.name) match {
              case Some((classes, properMethod)) =>
                val containingClass = m.containingClass
                val fondClass = classes.find { clazz =>
                  val instance = ScalaPsiManager.instance(holder.getProject)
                  val baseClass = instance.getCachedClass(m.resolveScope, clazz).orNull
                  baseClass != null && containingClass != null &&
                    containingClass.sameOrInheritor(baseClass)
                }
                fondClass match {
                  case Some(_) =>
                    val isForJavaOnly = ref.parents.exists {
                      case docOwner: ScDocCommentOwner => docOwner.docComment.exists(_.getText.contains("for Java only"))
                      case _                           => false
                    }

                    if (!isForJavaOnly) {
                      val properMethodText = properMethod match {
                        case Some(m) => "\n" + s"""Proper scala method: <a href="psi_element://$m">$m</a>""".stripMargin
                        case None    => ""
                      }

                      val message = "" +
                        "<html>" +
                        "Don't use this method, use appropriate method implemented for Scala, " +
                        "or use \"for Java only\" text in bounded doc comment owner ScalaDoc." +
                        properMethodText +
                        "</html>"
                      holder.registerProblem(ref.nameId, message, ProblemHighlightType.LIKE_DEPRECATED)
                    }
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

private object ScalaWrongPlatformMethodsUsageInspection {

  private val methodNameToClasses: Map[String, (Seq[String], Option[String])] = Map(
    ("getContainingClass", (Seq("com.intellij.psi.PsiMember"),
      Some("org.jetbrains.plugins.scala.extensions.PsiMemberExt.containingClass")
    )),
    ("getQualifiedName", (Seq("com.intellij.psi.PsiClass"),
      Some("org.jetbrains.plugins.scala.extensions.PsiClassExt.qualifiedName"))
    ),
    ("getName", (
      Seq(
        "com.intellij.navigation.NavigationItem",
        "com.intellij.psi.PsiNamedElement",
        "org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction",
        "org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement"
      ),
      Some("org.jetbrains.plugins.scala.extensions.PsiNamedElementExt.name"))
    ),
    ("hasModifierProperty", (Seq("com.intellij.psi.PsiModifierListOwner"),
      Some("org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt._")
    )),
    ("getClasses", (Seq("com.intellij.psi.PsiClassOwner"), None)),
    ("getClassNames", (Seq("com.intellij.psi.PsiClassOwnerEx"), None)),
    ("putUserData", (Seq("com.intellij.openapi.project.Project", "com.intellij.openapi.module.Module"),
      Some("org.jetbrains.plugins.scala.extensions.AnyRefExt.delegateUserDataHolder.putUserData")
    )),
  )
}
