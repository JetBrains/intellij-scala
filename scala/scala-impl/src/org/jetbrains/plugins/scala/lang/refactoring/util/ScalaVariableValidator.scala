package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._

object ScalaVariableValidator {

  def empty = new ScalaVariableValidator(null, true, null, null)

  def apply(file: PsiFile, element: PsiElement, occurrences: Seq[TextRange]): ScalaVariableValidator = {
    val container = enclosingContainer(commonParent(file, occurrences))
    val containerOne = enclosingContainer(element)

    new ScalaVariableValidator(element, occurrences.isEmpty, container, containerOne)
  }
}

class ScalaVariableValidator(selectedElement: PsiElement, noOccurrences: Boolean, enclosingContainerAll: PsiElement, enclosingOne: PsiElement)
  extends ScalaValidator(selectedElement, noOccurrences, enclosingContainerAll, enclosingOne) {

  protected override def findConflictsImpl(name: String, allOcc: Boolean): Seq[(PsiNamedElement, String)] = { //returns declaration and message
    val container = enclosingContainer(allOcc)
    if (container == null) return Seq.empty
    val builder = Seq.newBuilder[(PsiNamedElement, String)]
    builder ++= validateDown(container, name, allOcc)
    builder ++= validateReference(selectedElement, name)
    var cl = container
    while (cl != null && !cl.isInstanceOf[ScTypeDefinition]) cl = cl.getParent
    if (cl != null) {
      cl match {
        case x: ScTypeDefinition =>
          for (member <- x.membersWithSynthetic) {
            member match {
              case x: ScVariable => for (el <- x.declaredElements if el.name == name)
                builder += ((el, messageForMember(el.name)))
              case x: ScValue => for (el <- x.declaredElements if el.name == name)
                builder += ((el, messageForMember(el.name)))
              case _ =>
            }
          }
          for (function <- x.functions; if function.name == name) {
            builder += ((x, messageForMember(function.name)))
          }
          x match {
            case scClass: ScClass =>
              for {
                constructor <- scClass.constructor
                parameter <- constructor.parameters
                if parameter.name == name
              } {
                builder += ((parameter, messageForClassParameter(parameter.name)))
              }
            case _ =>
          }
      }
    }
    builder.result()
  }

  private def validateReference(context: PsiElement, name: String): Seq[(PsiNamedElement, String)] = {
    ScalaPsiElementFactory.createExpressionFromText(name, context) match {
      case ResolvesTo(elem@ScalaPsiUtil.inNameContext(nameCtx)) =>
        val message = nameCtx match {
          case _: ScClassParameter => messageForClassParameter(name)
          case _: ScParameter => messageForParameter(name)
          case m: ScMember if m.isLocal =>
            if (m.getTextOffset < context.getTextOffset) messageForLocal(name)
            else ""
          case _: ScCaseClause | _: ScGenerator | _: ScForBinding => messageForLocal(name)
          case _: PsiMember => messageForMember(name)
          case _ => ""
        }
        if (message != "") Seq((elem, message))
        else Seq.empty
      case _ => Seq.empty
    }
  }

  private def validateDown(element: PsiElement, name: String, allOcc: Boolean): Seq[(PsiNamedElement, String)] = {
    val container = enclosingContainer(allOcc)
    val builder = Seq.newBuilder[(PsiNamedElement, String)]
    for (child <- element.getChildren) {
      child match {
        case x: ScClassParameter if x.name == name =>
          builder += ((x, messageForClassParameter(x.name)))
        case x: ScParameter if x.name == name =>
          builder += ((x, messageForParameter(x.name)))
        case x: ScFunctionDefinition if x.name == name =>
          builder += (if (x.isLocal) (x, messageForLocal(x.name)) else (x, messageForMember(x.name)))
        case x: ScBindingPattern if x.name == name =>
          builder += (if (x.isClassMember) (x, messageForMember(x.name)) else (x, messageForLocal(x.name)))
        case _ =>
      }
    }
    if (element != container)
      for (child <- element.getChildren) {
        builder ++= validateDown(child, name, allOcc)
      }
    else {
      var from = {
        var parent: PsiElement = if (allOcc) {
          selectedElement //todo:
        } else {
          selectedElement
        }
        if (PsiTreeUtil.isAncestor(container, parent, true))
          while (parent.getParent != null && parent.getParent != container) parent = parent.getParent
        else parent = container.getFirstChild
        parent
      }
      var fromDoubles = from.getPrevSibling
      var i = 0
      while (fromDoubles != null) {
        i = i + 1
        fromDoubles match {
          case x: ScVariableDefinition =>
            val elems = x.declaredElements
            for (elem <- elems; if elem.name == name)
              builder += (if (x.isLocal) (elem, messageForLocal(elem.name)) else (elem, messageForMember(elem.name)))
          case x: ScPatternDefinition =>
            val elems = x.declaredElements
            for (elem <- elems; if elem.name == name)
              builder += (if (x.isLocal) (elem, messageForLocal(elem.name)) else (elem, messageForMember(elem.name)))
          case _ =>
        }
        fromDoubles = fromDoubles.getPrevSibling
      }
      while (from != null) {
        builder ++= validateDown(from, name, allOcc)
        from = from.getNextSibling
      }
    }
    builder.result()
  }

  private def messageForMember(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.field", name)

  private def messageForLocal(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.local", name)

  private def messageForParameter(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.parameter", name)

  private def messageForClassParameter(name: String) = ScalaBundle.message("introduced.variable.will.conflict.with.class.parameter", name)
}
