package scala.macros.intellij.editor

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.macros.expansion.MacroExpansionLineMarkerProvider
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder

class Macros2ExpansionLineMarkerProvider extends MacroExpansionLineMarkerProvider {
  override protected def getExpandMarker(element: PsiElement): Option[Marker] = {
    import scala.macros.intellij.psiExt._

    element.getParent match {
      case holder: ScAnnotationsHolder => holder.annotations.find(_.isMacro2).map(a=>newExpandMarker(a.getFirstChild) { _=> })
      case _ => None
    }
  }

  override protected def getUndoMarker(element: PsiElement): Option[Marker] = None
}
