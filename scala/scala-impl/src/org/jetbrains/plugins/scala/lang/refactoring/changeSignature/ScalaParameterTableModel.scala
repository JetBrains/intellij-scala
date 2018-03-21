package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import javax.swing.table.TableCellEditor

import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, HighlightLevelUtil}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.refactoring.changeSignature.ParameterTableModelBase.{NameColumn, TypeColumn}
import com.intellij.refactoring.changeSignature._
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.ScalaParameterTableModel._
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaCodeFragmentTableCellEditor

import scala.collection.mutable.ArrayBuffer

/**
 * Nikolay.Tropin
 * 2014-08-29
 */
class ScalaParameterTableModel(typeContext: PsiElement,
                               defaultValueContext: PsiElement,
                               methodDescriptor: ScalaMethodDescriptor,
                               columnInfos: ColumnInfo[_, _]*)
        extends ParameterTableModelBase[ScalaParameterInfo, ScalaParameterTableModelItem](typeContext, defaultValueContext, columnInfos: _*) {

  val project = defaultValueContext.getProject
  val initialParams: Seq[Seq[ScalaParameterInfo]] = methodDescriptor.parameters

  private val codeFragments = ArrayBuffer[PsiElement]()

  def this(typeContext: PsiElement, defaultValueContext: PsiElement, methodDescriptor: ScalaMethodDescriptor) {
    this(typeContext, defaultValueContext, methodDescriptor,
      new ScalaNameColumn(typeContext.getProject),
      new ScalaTypeColumn(typeContext.getProject),
      new ScalaDefaultValueColumn(typeContext.getProject))
  }

  override def createRowItem(parameterInfo: ScalaParameterInfo): ScalaParameterTableModelItem = {
    val info = Option(parameterInfo).getOrElse(ScalaParameterInfo(project))

    val paramTypeCodeFragment = new ScalaCodeFragment(project, info.typeText(typeContext))
    val defaultValueCodeFragment = new ScalaCodeFragment(project, info.getDefaultValue)

    val fragments = Seq(paramTypeCodeFragment, defaultValueCodeFragment)
    codeFragments ++= fragments
    fragments.foreach(HighlightLevelUtil.forceRootHighlighting(_, FileHighlightingSetting.SKIP_HIGHLIGHTING))

    paramTypeCodeFragment.setContext(typeContext.getParent, typeContext)
    defaultValueCodeFragment.setContext(defaultValueContext.getParent, defaultValueContext)

    defaultValueCodeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE)

    val newClauseParams = initialParams.flatMap(_.headOption).drop(1)
    val startsNewClause = newClauseParams.contains(parameterInfo)

    new ScalaParameterTableModelItem(info, paramTypeCodeFragment, defaultValueCodeFragment, startsNewClause)(typeContext)
  }

  def clear(): Unit = {
    codeFragments.foreach(HighlightLevelUtil.forceRootHighlighting(_, FileHighlightingSetting.NONE))
  }
}

object ScalaParameterTableModel {

  class ScalaTypeColumn(project: Project) extends TypeColumn[ScalaParameterInfo, ScalaParameterTableModelItem](project, ScalaFileType.INSTANCE) {
    override def doCreateEditor(o: ScalaParameterTableModelItem): TableCellEditor = new ScalaCodeFragmentTableCellEditor(project)
  }

  class ScalaNameColumn(project: Project) extends NameColumn[ScalaParameterInfo, ScalaParameterTableModelItem](project)

  class ScalaDefaultValueColumn(project: Project)
    extends ParameterTableModelBase.DefaultValueColumn[ScalaParameterInfo, ScalaParameterTableModelItem](project, ScalaFileType.INSTANCE) {

    override def doCreateEditor(item: ScalaParameterTableModelItem): TableCellEditor = new ScalaCodeFragmentTableCellEditor(project)
  }
}
