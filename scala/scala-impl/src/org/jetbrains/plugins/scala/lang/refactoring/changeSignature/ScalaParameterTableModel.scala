package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, HighlightLevelUtil}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.refactoring.changeSignature._
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.ScalaParameterTableModel._
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaCodeFragmentTableCellEditor

import javax.swing.table.TableCellEditor
import scala.collection.mutable.ArrayBuffer

class ScalaParameterTableModel(typeContext: PsiElement,
                               defaultValueContext: PsiElement,
                               methodDescriptor: ScalaMethodDescriptor,
                               columnInfos: ColumnInfo[_, _]*)
        extends ParameterTableModelBase[ScalaParameterInfo, ScalaParameterTableModelItem](typeContext, defaultValueContext, columnInfos: _*) {

  private implicit val project: Project = defaultValueContext.getProject
  val initialParams: Seq[Seq[ScalaParameterInfo]] = methodDescriptor.parameters

  private val codeFragments = ArrayBuffer[PsiElement]()

  def this(typeContext: PsiElement, defaultValueContext: PsiElement, methodDescriptor: ScalaMethodDescriptor) = {
    this(typeContext, defaultValueContext, methodDescriptor,
      new ScalaNameColumn(typeContext.getProject),
      new ScalaTypeColumn(typeContext.getProject),
      new ScalaDefaultValueColumn(typeContext.getProject))
  }

  override def createRowItem(parameterInfo: ScalaParameterInfo): ScalaParameterTableModelItem = {
    val info = Option(parameterInfo).getOrElse(ScalaParameterInfo(project))

    val paramTypeCodeFragment = ScalaCodeFragment(info.typeText(typeContext), typeContext.getParent, typeContext)
    val defaultValueCodeFragment = ScalaCodeFragment(info.getDefaultValue, defaultValueContext.getParent, defaultValueContext)

    val fragments = Seq(paramTypeCodeFragment, defaultValueCodeFragment)
    codeFragments ++= fragments
    fragments.foreach(HighlightLevelUtil.forceRootHighlighting(_, FileHighlightingSetting.SKIP_HIGHLIGHTING))

    defaultValueCodeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE)

    val newClauseParams = initialParams.flatMap(_.headOption).drop(1)
    val startsNewClause = newClauseParams.contains(parameterInfo)

    new ScalaParameterTableModelItem(info, paramTypeCodeFragment, defaultValueCodeFragment, startsNewClause)(typeContext)
  }

  def clear(): Unit = {
    codeFragments.clear()
  }
}

object ScalaParameterTableModel {

  class ScalaTypeColumn(project: Project) extends Columns.TypeColumn[ScalaParameterInfo, ScalaParameterTableModelItem](project, ScalaFileType.INSTANCE) {
    override def doCreateEditor(o: ScalaParameterTableModelItem): TableCellEditor = new ScalaCodeFragmentTableCellEditor(project)
  }

  class ScalaNameColumn(project: Project) extends Columns.NameColumn[ScalaParameterInfo, ScalaParameterTableModelItem](project)

  class ScalaDefaultValueColumn(project: Project)
    extends Columns.DefaultValueColumn[ScalaParameterInfo, ScalaParameterTableModelItem](project, ScalaFileType.INSTANCE) {

    override def doCreateEditor(item: ScalaParameterTableModelItem): TableCellEditor = new ScalaCodeFragmentTableCellEditor(project)
  }
}
