package org.jetbrains.plugins.scala.testingSupport.scalaTest.locationProvider

import com.intellij.execution.{Location, PsiLocation}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiElement, PsiClass}
import com.intellij.testIntegration.TestLocationProvider
import java.lang.String
import java.util.{ArrayList, List}

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.02.2009
 */

class ScalaTestLocationProvider extends TestLocationProvider {
  def getLocation(protocolId: String, locationData: String, project: Project): List[Location[_ <: PsiElement]] = {
    protocolId match {
      case "scala" => {
        val res = new ArrayList[Location[_ <: PsiElement]]()
        val facade = JavaPsiFacade.getInstance(project)
        val clazz: PsiClass = facade.findClass(locationData, GlobalSearchScope.allScope(project))
        if (clazz != null) res.add(PsiLocation.fromPsiElement[PsiClass](project, clazz))
        res
      }
      case _ => new ArrayList[Location[_ <: PsiElement]]()
    }
  }
}