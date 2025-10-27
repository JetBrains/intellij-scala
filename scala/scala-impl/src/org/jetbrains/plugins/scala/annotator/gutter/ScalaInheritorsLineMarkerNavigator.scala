package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait

import java.awt.event.MouseEvent
import java.util

/**
 * Java version is in [[com.intellij.codeInsight.daemon.impl.InheritorsLineMarkerNavigator]]
 * that delegates to [[com.intellij.codeInsight.navigation.GotoImplementationHandler]]
 */
private class ScalaInheritorsLineMarkerNavigator extends GutterIconNavigationHandler[PsiElement]{
  override def navigate(event: MouseEvent, element: PsiElement): Unit = {
    element.parent.collect {
      case aClass: PsiClass =>
        val inheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY)
        if (inheritors.nonEmpty) {
          val cname = aClass.name
          val (title, findUsagesTitle) =
            if (aClass.isInstanceOf[ScTrait]) {
              ScalaBundle.message("navigation.title.inheritors.trait", cname, inheritors.length.toString) ->
                ScalaBundle.message("navigation.findUsages.title.inheritors.trait", cname)
            } else {
              ScalaBundle.message("navigation.title.inheritors.class", cname, inheritors.length.toString) ->
                ScalaBundle.message("navigation.findUsages.title.inheritors.class", cname)
            }

          val renderer = new PsiClassListCellRenderer
          util.Arrays.sort(inheritors, renderer.getComparator)
          ScalaMarkerType.navigate(event, inheritors, aClass.getProject, title, findUsagesTitle, renderer)
        }
    }
  }
}
