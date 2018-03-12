package org.jetbrains.plugins.scala.lang.formatting.settings

import java.util
import javax.swing._

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.Binding
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter
import org.jetbrains.plugins.scala.lang.formatting.settings.TypeAnnotationsPanel._

/**
  * @author Pavel Fatin
  */
class TypeAnnotationsPanel(settings: CodeStyleSettings) extends TypeAnnotationsPanelBase(settings) {
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
        TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS, myObviousType.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_WHEN_TYPE_IS_OBVIOUS = _, myObviousType.setSelected),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES, myTestSources.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_IN_TEST_SOURCES = _, myTestSources.setSelected),
      new Binding(
        TYPE_ANNOTATION_EXCLUDE_IN_SCRIPT, myScript.isSelected)
      (TYPE_ANNOTATION_EXCLUDE_IN_SCRIPT = _, myScript.setSelected),

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

  override protected def getTabTitle: String = "Type Annotations"

  override protected def getRightMargin: Int = 0

  override protected def createHighlighter(scheme: EditorColorsScheme): ScalaEditorHighlighter =
    new ScalaEditorHighlighter(null, null, scheme)

  override protected def getFileType: FileType = ScalaFileType.INSTANCE

  override protected def getPreviewText: String = ""

  override protected def getPanel: JComponent = myContent

  override protected def isModified(settings: CodeStyleSettings): Boolean =
    bindingsFor(settings).exists(!_.leftEqualsRight)

  override protected def resetImpl(settings: CodeStyleSettings): Unit =
    bindingsFor(settings).foreach(it => it.copyLeftToRight())

  override protected def apply(settings: CodeStyleSettings): Unit = {
    bindingsFor(settings).foreach(it => it.copyRightToLeft())

    Option(ProjectUtil.guessCurrentProject(myContent)).foreach { project =>
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }
}

private object TypeAnnotationsPanel {
  private def elementsIn(field: TextFieldWithBrowseButton): util.Set[String] =
    new util.HashSet(ParametersListUtil.COLON_LINE_PARSER.fun(field.getText))

  private def setElementsIn(field: TextFieldWithBrowseButton, elements: util.Set[String]): Unit =
    field.setText(ParametersListUtil.COLON_LINE_JOINER.fun(new util.ArrayList(elements)))
}
