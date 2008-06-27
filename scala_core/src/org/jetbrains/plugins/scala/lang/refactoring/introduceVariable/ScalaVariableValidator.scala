package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTryBlock
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScEnumerator
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenerator
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.PsiElement

/**
* User: Alexander Podkhalyuzin
* Date: 24.06.2008
*/

class ScalaVariableValidator(introduceVariableBase: ScalaIntroduceVariableBase,
                            myProject: Project,
                            selectedExpr: ScExpression,
                            occurrences: Array[ScExpression],
                            enclosingContainer: PsiElement) extends ScalaValidator {
  def getProject(): Project = {
    myProject
  }

  def isOK(dialog: ScalaIntroduceVariableDialogInterface): Boolean = {
    val name = dialog.getEnteredName
    val allOcc = dialog.isReplaceAllOccurrences
    val conflicts = isOKImpl(name, allOcc)
    return conflicts.length == 0 || introduceVariableBase.reportConflicts(conflicts, myProject)
  }

  private def isOKImpl(name: String, allOcc: Boolean): Array[String] = {
    validateDown(enclosingContainer, name, allOcc)
  }

  private def validateDown(element: PsiElement, name: String, allOcc: Boolean): Array[String] = {
    val buf = new ArrayBuffer[String]
    for (child <- element.getChildren) {
      child match {
        case x: ScParameter => {
          if (x.name == name) {
            buf += ScalaBundle.message("introduced.variable.will.conflict.with.parameter", Array[Object](x.name))
          }
        }
        case x: ScFunctionDefinition => {
          if (x.name == name) {
            buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", Array[Object](x.name))
          }
        }
        case x: ScBindingPattern => {
          if (x.name == name) {
            buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", Array[Object](x.name))
          }
        }
        case _ =>
      }
    }
    if (element != enclosingContainer)
      for (child <- element.getChildren) {
        buf ++= validateDown(child, name, allOcc)
      }
    else {
      var from = {
        var parent: PsiElement = if (allOcc) {
          occurrences(0)
        } else {
          selectedExpr
        }
        while (parent.getParent != null && parent.getParent != enclosingContainer) parent = parent.getParent
        parent
      }
      var fromDoubles = from.getPrevSibling
      while (fromDoubles != null) {
        fromDoubles match {
          case x: ScVariableDefinition => {
            val elems = x.declaredElements
            for (elem <- elems) {
              if (elem.name == name) {
                buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", Array[Object](elem.name))
              }
            }
          }
          case x: ScPatternDefinition => {
            val elems = x.declaredElements
            for (elem <- elems) {
              if (elem.name == name) {
                buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", Array[Object](elem.name))
              }
            }
          }
          case _ =>
        }
        fromDoubles = fromDoubles.getPrevSibling
      }
      while (from != null) {
        buf ++= validateDown(from, name, allOcc)
        from = from.getNextSibling
      }
    }
    return buf.toArray
  }

  def validateName(name: String, increaseNumber: Boolean): String = name
}