package org.jetbrains.bsp.project.test

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestRunLineMarkerProvider

import scala.collection.JavaConverters._

class BspTestRunLineMarkerProvider extends ScalaTestRunLineMarkerProvider {

  def isBspTestClass(cl: PsiClass): Boolean = {
    val name = cl.getQualifiedName
    // TODO find a way to obtain the "root" module instance in order not to iterate over all modules
    ModuleManager.getInstance(cl.getProject).getModules.toList
      .flatMap(BspMetadata.get(cl.getProject, _))
      .flatMap(x => x.testClasses.asScala.toList)
      // TODO try to find a proper way for matching the class names
      .exists(tc => name.contains(tc) || tc.contains(name))
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
