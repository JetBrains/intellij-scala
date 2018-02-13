package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

object InspectionsUtil {
  val ERROR_HANDLING = "(Scala) Error Handling"

  val SCALA = "Scala: General"

  val SCALADOC = "Scala: Scaladoc"

  val MethodSignature = "Scala: Method signature"

  def isExpressionOfType(className: String, expr: ScExpression): Boolean = Option(expr).map { expression =>
    (expression.`type`().getOrAny, expression.getProject)
  }.exists {
    case (tp, project) => conformsToTypeFromClass(tp, className, project)
  }

  def conformsToTypeFromClass(scType: ScType, className: String, project: Project): Boolean = {
    implicit val ctx: ProjectContext = project

    def typeFromClassName(fqn: String, project: Project): Option[ScType] = {
      val clazz = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
      Option(clazz).map { c =>
        val designatorType = ScDesignatorType(c)
        c.getTypeParameters.toSeq match {
          case Seq() => designatorType
          case params =>
            ScParameterizedType(designatorType, params.map(UndefinedType(_)))
        }
      }
    }

    if (scType == Null || scType == Nothing) false
    else typeFromClassName(className, project).exists(scType.conforms(_))
  }
}