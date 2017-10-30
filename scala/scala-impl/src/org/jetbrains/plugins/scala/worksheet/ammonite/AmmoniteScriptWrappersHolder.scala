package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.worksheet.GotoOriginalHandlerUtil

import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 03.08.17.
  */
class AmmoniteScriptWrappersHolder(project: Project) extends AbstractProjectComponent(project) {
  private val file2object = mutable.WeakHashMap.empty[ScalaFile, (ScObject, Long)]
  
  private def createWrapper(from: ScalaFile) = {
    val obj = GotoOriginalHandlerUtil.createPsi((from: ScalaFile) => ScalaPsiElementFactory.createObjectWithContext(
      s"object ${AmmoniteScriptWrappersHolder.getWrapperName(from)} {\n${from.getText} }", from, from.getFirstChild
    ), from)
    GotoOriginalHandlerUtil.storePsi(obj.getContainingFile, from)
    obj
  }
  
  def findWrapper(base: ScalaFile): Option[ScObject] = {
    if (!AmmoniteUtil.isAmmoniteFile(base)) None else {
      file2object.get(base) match {
        case Some((wrapper, timestamp)) if timestamp == base.getModificationStamp && wrapper.isValid => Option(wrapper)
        case _ => 
          val wrapper = createWrapper(base)
          val timestamp = base.getModificationStamp
          file2object.put(base, (wrapper, timestamp))
          Option(wrapper)
      }
    }
  }
}

object AmmoniteScriptWrappersHolder {
  def getInstance(project: Project): AmmoniteScriptWrappersHolder = project.getComponent(classOf[AmmoniteScriptWrappersHolder])
  
  def getWrapperName(from: PsiFile): String = from.getName.stripSuffix(s".${AmmoniteUtil.AMMONITE_EXTENSION}")
  
  def getOffsetFix(from: PsiFile): Int = (getWrapperName(from) + "object  {\n").length 
}