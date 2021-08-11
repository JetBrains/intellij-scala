package org.jetbrains.plugins.scala
package lang
package formatting
package settings

import java.util
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.util.execution.ParametersListUtil

import javax.swing._
import org.jetbrains.plugins.scala.extensions.Binding

/**
  * @author Pavel Fatin
  */
final class TypeAnnotationsPanel(settings: CodeStyleSettings) extends TypeAnnotationsPanelBase(settings) {

  import TypeAnnotationsPanel._

  private def bindingsFor(settings: CodeStyleSettings): Seq[Binding[_]] = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    import scalaSettings._

    Seq(
      new Binding(
        TYPE_ANNOTATION_PUBLIC_MEMBER, myPublicMember.isSelected)
      (TYPE_ANNOTATION_PUBLIC_MEMBER = _, myPublicMember.setSelected),
      new Binding(
        TYPE_ANNOTATION_PROTECTED_MEMBER, myProtectedMember.isSelected)
      (TYPE_ANNOTATION_PROTECTED_MEMBER = _, myProtectedMember.setSelected),
      new Binding(
        TYPE_ANNOTATION_PRIVATE_MEMBER, myPrivateMember.isSelected)
      (TYPE_ANNOTATION_PRIVATE_MEMBER = _, myPrivateMember.setSelected),
      new Binding(
        TYPE_ANNOTATION_LOCAL_DEFINITION, myLocalDefinition.isSelected)
      (TYPE_ANNOTATION_LOCAL_DEFINITION = _, myLocalDefinition.setSelected),
      new Binding(
        TYPE_ANNOTATION_FUNCTION_PARAMETER, myFunctionParameter.isSelected)
      (TYPE_ANNOTATION_FUNCTION_PARAMETER = _, myFunctionParameter.setSelected),
      new Binding(
        TYPE_ANNOTATION_UNDERSCORE_PARAMETER, myUnderscoerParameter.isSelected)
      (TYPE_ANNOTATION_UNDERSCORE_PARAMETER = _, myUnderscoerParameter.setSelected),

      new Binding(
        TYPE_ANNOTATION_IMPLICIT_MODIFIER, myImplicitModifier.isSelected)
      (TYPE_ANNOTATION_IMPLICIT_MODIFIER = _, myImplicitModifier.setSelected),
      new Binding(
        TYPE_ANNOTATION_UNIT_TYPE, myUnitType.isSelected)
      (TYPE_ANNOTATION_UNIT_TYPE = _, myUnitType.setSelected),
      new Binding(
        TYPE_ANNOTATION_STRUCTURAL_TYPE, myStructuralType.isSelected)
      (TYPE_ANNOTATION_STRUCTURAL_TYPE = _, myStructuralType.setSelected),

      new Binding(
        TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS, myMemberOfAnonymousClass.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_ANONYMOUS_CLASS = _, myMemberOfAnonymousClass.setSelected),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS, myMemberOfPrivateClass.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_MEMBER_OF_PRIVATE_CLASS = _, myMemberOfPrivateClass.setSelected),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_CONSTANT, myConstant.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_CONSTANT = _, myConstant.setSelected),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_STABLE, myStableType.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_STABLE = _, myStableType.setSelected),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES, myTestSources.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES = _, myTestSources.setSelected),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES, myDialectSources.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_IN_DIALECT_SOURCES = _, myDialectSources.setSelected),

      new Binding(
        TYPE_ANNOTATION_EXCLUDE_MEMBER_OF, elementsIn(myMembers))
      (TYPE_ANNOTATION_EXCLUDE_MEMBER_OF = _, setElementsIn(myMembers, _)),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH, elementsIn(myAnnotations))
      (TYPE_ANNOTATION_EXCLUDE_ANNOTATED_WITH = _, setElementsIn(myAnnotations, _)),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES, elementsIn(myTypes))
      (TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_MATCHES = _, setElementsIn(myTypes, _))
    )
  }

  private var projectOpt: Option[Project] = None

  def onProjectSet(aProject: Project): Unit = {
    projectOpt = Some(aProject)
  }

  override def dispose(): Unit = {
    projectOpt = None
    super.dispose()
  }

  override protected def getPanelInner: JComponent = myContent

  override def isModified(settings: CodeStyleSettings): Boolean =
    bindingsFor(settings).exists(!_.leftEqualsRight)

  override protected def resetImpl(settings: CodeStyleSettings): Unit =
    bindingsFor(settings).foreach(it => it.copyLeftToRight())

  override def apply(settings: CodeStyleSettings): Unit = {
    bindingsFor(settings).foreach(it => it.copyRightToLeft())

    projectOpt.foreach { project =>
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }
}

object TypeAnnotationsPanel {
  private def elementsIn(field: TextFieldWithBrowseButton): util.Set[String] =
    new util.HashSet(ParametersListUtil.COLON_LINE_PARSER.fun(field.getText))

  private def setElementsIn(field: TextFieldWithBrowseButton, elements: util.Set[String]): Unit =
    field.setText(NlsString.force(ParametersListUtil.COLON_LINE_JOINER.fun(new util.ArrayList(elements))))
}
