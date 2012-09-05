package org.jetbrains.plugins.scala.testingSupport.locationProvider

import com.intellij.execution.{Location, PsiLocation}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testIntegration.TestLocationProvider
import java.lang.String
import java.util.{ArrayList, List}
import com.intellij.openapi.editor.Document
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.02.2009
 *
 * For Specs, Specs2 and ScalaTest
 */

class ScalaTestLocationProvider extends TestLocationProvider {
  private val SpecsHintPattern = """(\S+)\?filelocation=(.+):(.+)""".r

  private val ScalaTestTopOfClassPattern = """TopOfClass:(\S+)TestName:(.+)""".r
  private val ScalaTestTopOfMethodPattern = """TopOfMethod:(\S+):(\S+)TestName:(.+)""".r
  private val ScalaTestLineInFinePattern = """LineInFile:(\S+):(.+):(.+)TestName:(.+)""".r

  def getLocation(protocolId: String, locationData: String, project: Project): List[Location[_ <: PsiElement]] = {
    protocolId match {
      case "scala" =>
        locationData match {
          case SpecsHintPattern(className, fileName, lineNumber) =>
            val clazzes = ScalaPsiManager.instance(project).getCachedClasses(GlobalSearchScope.allScope(project), className)
            val found = clazzes.find(c => Option(c.getContainingFile).map(_.name == fileName).getOrElse(false))

            found match {
              case Some(file) =>
                val res = new ArrayList[Location[_ <: PsiElement]]()
                res.add(createLocationFor(project, file.getContainingFile, lineNumber.toInt))
                res
              case _ => searchForClassByUnqualifiedName(project, className)
            }
          case x => searchForClassByUnqualifiedName(project, locationData)
        }
      case "scalatest" =>
        val res = new ArrayList[Location[_ <: PsiElement]]()
        locationData match {
          case ScalaTestTopOfClassPattern(className, testName) =>
            val clazz: PsiClass = ScalaPsiManager.instance(project).getCachedClass(className,
              GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE)
            if (clazz != null) res.add(new PsiLocationWithName(project, clazz, testName))
          case ScalaTestTopOfMethodPattern(className, methodName, testName) =>
            val clazz: PsiClass = ScalaPsiManager.instance(project).getCachedClass(className,
              GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE)
            clazz match {
              case td: ScTypeDefinition =>
                td.signaturesByName(methodName).foreach {
                  case signature: PhysicalSignature =>
                    res.add(new PsiLocationWithName(project, signature.method, testName))
                }
              case _ =>
                if (clazz != null) {
                  val methods = clazz.findMethodsByName(methodName, false)
                  methods.foreach {
                    method => res.add(new PsiLocationWithName(project, method, testName))
                  }
                }
            }
          case ScalaTestLineInFinePattern(className, fileName, lineNumber, testName) =>
            val clazzes: Array[PsiClass] =
              ScalaPsiManager.instance(project).getCachedClasses(GlobalSearchScope.allScope(project), className)
            val found = clazzes.find(c => Option(c.getContainingFile).map(_.name == fileName).getOrElse(false))
            found match {
              case Some(file) =>
                res.add(createLocationFor(project, file.getContainingFile, lineNumber.toInt, Some(testName)))
              case _ =>
            }
          case _ =>
        }
        res
      case _ => new ArrayList[Location[_ <: PsiElement]]()
    }
  }

  private def searchForClassByUnqualifiedName(project: Project, locationData: String): ArrayList[Location[_ <: PsiElement]] = {
    val res = new ArrayList[Location[_ <: PsiElement]]()
    val clazz: PsiClass = ScalaPsiManager.instance(project).getCachedClass(locationData,
      GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE)
    if (clazz != null) res.add(PsiLocation.fromPsiElement[PsiClass](project, clazz))
    res
  }

  private def createLocationFor(project: Project, psiFile: PsiFile, lineNum: Int,
                                withName: Option[String] = None): Location[_ <: PsiElement] = {
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
      val length: Int = elementAtLine.getTextLength
      offset += (if (length > 1) length - 1 else 1)
    }
    withName match {
      case Some(testName) =>
        new PsiLocationWithName(project, if (elementAtLine != null) elementAtLine else psiFile, testName)
      case _ =>
        PsiLocation.fromPsiElement(project, if (elementAtLine != null) elementAtLine else psiFile)
    }
  }
}