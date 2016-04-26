package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

object InspectionsUtil {
  val ERROR_HANDLING = "(Scala) Error Handling"

  val SCALA = "Scala: General"

  val SCALADOC = "Scala: Scaladoc"

  val MethodSignature = "Scala: Method signature"

  def isExpressionOfType(className: String, expr: ScExpression): Boolean = {
    if (expr == null) return false
    val exprType = expr.getType().getOrAny
    conformsToTypeFromClass(exprType, className, expr.getProject)
  }

  def conformsToTypeFromClass(scType: ScType, className: String, project: Project)
                             (implicit typeSystem: TypeSystem = project.typeSystem): Boolean = {
    def typeFromClassName(fqn: String, project: Project): Option[ScType] = {
      val clazz = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
      Option(clazz).map { c =>
        val designatorType = ScDesignatorType(c)
        c.getTypeParameters.toSeq match {
          case Seq() => designatorType
          case params =>
            ScParameterizedType(designatorType, params.map {
              p => UndefinedType(TypeParameterType(p))
            })
        }
      }
    }

    if (scType == Null || scType == Nothing) false
    else typeFromClassName(className, project).exists(scType.conforms(_))
  }
}