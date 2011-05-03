package org.jetbrains.plugins.scala.testingSupport.locationProvider

import com.intellij.execution.{Location, PsiLocation}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testIntegration.TestLocationProvider
import java.lang.String
import java.util.{ArrayList, List}
import com.intellij.openapi.editor.Document
import com.intellij.psi._

/**
  * User: Alexander Podkhalyuzin
  * Date: 24.02.2009
  *
  * For Specs and Scalatest
  */
class ScalaTestLocationProvider extends TestLocationProvider {
  private val SpecsHintPattern = """(\S+)\?filelocation=(.+):(.+)""".r

  def getLocation(protocolId: String, locationData: String, project: Project): List[Location[_ <: PsiElement]] = {
    protocolId match {
      case "scala" =>
        locationData match {
          case SpecsHintPattern(className, fileName, lineNumber) =>
            val facade = JavaPsiFacade.getInstance(project)
            val clazzes: Array[PsiClass] = facade.findClasses(className, GlobalSearchScope.allScope(project))
            val found = clazzes.find(c => Option(c.getContainingFile).map(_.getName == fileName).getOrElse(false))

            found match {
              case Some(file) =>
                val res = new ArrayList[Location[_ <: PsiElement]]()
                res.add(createLocationFor(project, file.getContainingFile, lineNumber.toInt))
                res
              case _ => searchForClassByUnqualifiedName(project, className)
            }
          case x => searchForClassByUnqualifiedName(project, locationData)
        }
      case _ => new ArrayList[Location[_ <: PsiElement]]()
    }
  }

  private def searchForClassByUnqualifiedName(project: Project, locationData: String): ArrayList[Location[_ <: PsiElement]] = {
    val res = new ArrayList[Location[_ <: PsiElement]]()
    val facade = JavaPsiFacade.getInstance(project)
    val clazz: PsiClass = facade.findClass(locationData, GlobalSearchScope.allScope(project))
    if (clazz != null) res.add(PsiLocation.fromPsiElement[PsiClass](project, clazz))
    res
  }

  private def createLocationFor(project: Project, psiFile: PsiFile, lineNum: Int): Location[_ <: PsiElement] = {
    assert(lineNum > 0)
    val doc: Document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
    if (doc == null) {
      return null
    }
    val lineCount: Int = doc.getLineCount
    var lineStartOffset: Int = 0
    var endOffset: Int = 0
    if (lineNum <= lineCount) {
      lineStartOffset = doc.getLineStartOffset(lineNum - 1)
      endOffset = doc.getLineEndOffset(lineNum - 1)
    }
    else {
      lineStartOffset = 0
      endOffset = doc.getTextLength
    }
    var offset: Int = lineStartOffset
    var elementAtLine: PsiElement = null
    var found = false
    while (offset <= endOffset && !found) {
      elementAtLine = psiFile.findElementAt(offset)
      if (!(elementAtLine.isInstanceOf[PsiWhiteSpace])) {
        found = true
      }
      var length: Int = elementAtLine.getTextLength
      offset += (if (length > 1) length - 1 else 1)
    }
    return PsiLocation.fromPsiElement(project, if (elementAtLine != null) elementAtLine else psiFile)
  }
}