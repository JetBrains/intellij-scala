package org.jetbrains.bsp.project.test

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestRunLineMarkerProvider

class BspTestRunLineMarkerProvider extends ScalaTestRunLineMarkerProvider {

  def isBspTestClass(cl: PsiClass): Boolean = {
    val name = cl.getQualifiedName
    // TODO find a way to obtain the "root" module instance in order not to iterate over all modules
    BspMetadata.findScalaTestClasses(cl.getProject)
      .exists { case (_, tc) => name.contains(tc) || tc.contains(name) }
  }

  override def getInfo(e: PsiElement): RunLineMarkerContributor.Info = {
    if (!super.isIdentifier(e))
      return null
    val element: PsiElement = e.getParent
    element match {
      // TODO this is very inneficient, doing this for all classess
      case cl: PsiClass if isBspTestClass(cl) =>
        super.getInfo("java:suite://" + cl.getQualifiedName, e.getProject, isClass = true)
      case _ => null
    }
  }
}
