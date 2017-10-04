package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectContext
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
      s" object ${from.getName.stripSuffix(s".${AmmoniteUtil.AMMONITE_EXTENSION}")} {  }", from, from.getFirstChild
    ), from)
    
    GotoOriginalHandlerUtil.storePsi(obj.getContainingFile, from)

    def storeInfo(psi: PsiElement) {
      val psiElement = GotoOriginalHandlerUtil.createPsi((p: PsiElement) => ScalaPsiElementFactory.createElementFromText(p.getText)( //todo
        new ProjectContext(from.getProject)), psi)
      obj.extendsBlock.templateBody.foreach { body => body.addBefore(psiElement, body.getLastChild)}
    }

    from.getChildren.foreach {
      case decl: ScDeclaration => storeInfo(decl)
      case df@(_: ScVariableDefinition | _: ScTemplateDefinition | _: ScFunctionDefinition | _: ScPatternDefinition | _: ScTypeDefinition) =>
        storeInfo(df)
      case _ => 
    }
    
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
}