package org.jetbrains.plugins.scala
package annotator

trait OverridingAnnotatorTestBase extends AnnotatorSimpleTestCase {

  import lang.psi.api._
  import statements._

  def messages(code: String): List[Message] = {
    val annotator = new OverridingAnnotator() {}
    val file = ("\n" + code).parseWithEventSystem

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    new ScalaRecursiveElementVisitor {

      override def visitFunction(function: ScFunction): Unit = {
        annotator.checkOverrideMethods(function)
        super.visitFunction(function)
      }

      override def visitTypeAlias(alias: ScTypeAlias): Unit = {
        annotator.checkOverrideTypeAliases(alias)
        super.visitTypeAlias(alias)
      }

      override def visitVariable(variable: ScVariable): Unit = {
        annotator.checkOverrideVariables(variable)
        super.visitVariable(variable)
      }

      override def visitValue(value: ScValue): Unit = {
        annotator.checkOverrideValues(value)
        super.visitValue(value)
      }

      override def visitClassParameter(parameter: params.ScClassParameter): Unit = {
        annotator.checkOverrideClassParameters(parameter)
        super.visitClassParameter(parameter)
      }

      override def visitModifierList(modifierList: base.ScModifierList): Unit = {
        modifiers.ModifierChecker.checkModifiers(modifierList)
        super.visitModifierList(modifierList)
      }
    }.visitFile(file)

    mock.annotations
  }
}
