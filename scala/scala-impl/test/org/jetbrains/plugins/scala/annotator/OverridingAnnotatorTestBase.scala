package org.jetbrains.plugins.scala.annotator
import org.jetbrains.plugins.scala.annotator.modifiers.ModifierChecker
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
  * Created by mucianm on 22.03.16.
  */
trait OverridingAnnotatorTestBase extends SimpleTestCase{

  final val Header = "\n"

  def messages(code: String): List[Message] = {
    val annotator = new OverridingAnnotator() {}
    val file = (Header + code).parse

    val mock = new AnnotatorHolderMock(file)

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitFunction(fun: ScFunction): Unit = {
        if (fun.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideMethods(fun, mock, isInSources = false)
        }
        super.visitFunction(fun)
      }

      override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
        if (typedef.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideTypes(typedef, mock)
        }
        super.visitTypeDefinition(typedef)
      }

      override def visitTypeAlias(alias: ScTypeAlias): Unit = {
        if (alias.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideTypes(alias, mock)
        }
        super.visitTypeAlias(alias)
      }

      override def visitVariable(varr: ScVariable): Unit = {
        if (varr.getParent.isInstanceOf[ScTemplateBody] ||
          varr.getParent.isInstanceOf[ScEarlyDefinitions]) {
          annotator.checkOverrideVars(varr, mock, isInSources = false)
        }
        super.visitVariable(varr)
      }

      override def visitValue(v: ScValue): Unit = {
        if (v.getParent.isInstanceOf[ScTemplateBody] ||
          v.getParent.isInstanceOf[ScEarlyDefinitions]) {
          annotator.checkOverrideVals(v, mock, isInSources = false)
        }
        super.visitValue(v)
      }

      override def visitClassParameter(parameter: ScClassParameter): Unit = {
        annotator.checkOverrideClassParameters(parameter, mock)
        super.visitClassParameter(parameter)
      }

      override def visitModifierList(modifierList: ScModifierList) {
        ModifierChecker.checkModifiers(modifierList, mock)
        super.visitModifierList(modifierList)
      }
    }

    file.accept(visitor)

    mock.annotations
  }
}
