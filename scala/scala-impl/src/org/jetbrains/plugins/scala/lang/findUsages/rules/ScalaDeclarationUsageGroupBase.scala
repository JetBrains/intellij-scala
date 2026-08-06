package org.jetbrains.plugins.scala.lang.findUsages.rules

import com.intellij.navigation.NavigationItemFileStatus
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataSink, UiDataProvider}
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.FileStatus
import com.intellij.psi.{SmartPointerManager, SmartPsiElementPointer}
import com.intellij.usageView.UsageInfo
import com.intellij.usages.{UsageGroup, UsageView}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

import javax.swing.Icon

/**
 * @param myName using it as a parameter to handle `val` pattern definitions:
 *               whole `val` definition is a member but without a name (example: `val (x, y) = (1, 2)`
 */
private abstract class ScalaDeclarationUsageGroupBase(member: ScMember, private val myName: String) extends UsageGroup with UiDataProvider {
  private val myIcon: Icon = member.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS)

  private val myClassPointer: SmartPsiElementPointer[ScMember] =
    SmartPointerManager.getInstance(member.getProject).createSmartPsiElementPointer(member)

  override def getIcon: Icon = myIcon

  private def getElementSafe: ScMember = myClassPointer.getElement

  override def isValid: Boolean = {
    val psiClass = getElementSafe
    psiClass != null && psiClass.isValid
  }

  override def navigate(focus: Boolean): Unit = {
    if (canNavigate) {
      getElementSafe.navigate(focus)
    }
  }

  override def canNavigate: Boolean = isValid

  override def canNavigateToSource: Boolean = canNavigate

  override def uiDataSnapshot(sink: DataSink): Unit = {
    sink.`lazy`(CommonDataKeys.PSI_ELEMENT, () => getElementSafe)
    sink.`lazy`(UsageView.USAGE_INFO_KEY, () => {
      val element = getElementSafe
      if (element == null) null
      else new UsageInfo(element)
    })
  }

  override def hashCode: Int = myName.hashCode

  override def equals(other: Any): Boolean = other match {
    case that: ScalaDeclarationUsageGroupBase if getClass == other.getClass =>
      myName == that.myName
    case _ => false
  }

  override def compareTo(usageGroup: UsageGroup): Int =
    getPresentableGroupText.compareToIgnoreCase(usageGroup.getPresentableGroupText)

  override def getFileStatus: FileStatus =
    if (isValid) NavigationItemFileStatus.get(getElementSafe)
    else null
}
