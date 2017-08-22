package scala.meta.intellij.editor

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.macros.expansion.MacroExpandAction.expandMetaAnnotation
import org.jetbrains.plugins.scala.lang.macros.expansion.{MacroExpandAction, MacroExpansionLineMarkerProvider}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder

import scala.meta.intellij.MetaExpansionsManager
import scala.meta.intellij.MetaExpansionsManager.isUpToDate

class MetaExpansionLineMarkerProvider extends MacroExpansionLineMarkerProvider {
  override protected def getExpandMarker(element: PsiElement): Option[Marker] = {
    import scala.meta.intellij.psiExt._
    element.getParent match {
      case holder: ScAnnotationsHolder =>
        val metaAnnot: Option[ScAnnotation] = holder.annotations.find(_.isMetaMacro)
        metaAnnot.map { annot =>
          MetaExpansionsManager.getCompiledMetaAnnotClass(annot) match {
            case Some(clazz) if isUpToDate(annot, clazz) => newExpandMarker(annot.getFirstChild)(_=>expandMetaAnnotation(annot))
            case _ => createNotCompiledLineMarker(annot.getFirstChild, annot)
          }
        }
      case _ => None
    }
  }
  override protected def getUndoMarker(element: PsiElement): Option[Marker] = None

}
