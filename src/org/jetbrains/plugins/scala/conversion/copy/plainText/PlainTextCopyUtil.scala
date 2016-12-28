package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiErrorElement, PsiFile, PsiFileFactory, PsiJavaFile}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.mutable

/**
  * Created by Kate Ustyuzhanina on 12/27/16.
  */
object PlainTextCopyUtil {
  val scalaFileMap: mutable.HashMap[String, Option[ScalaFile]] = mutable.HashMap.empty
  val javaFileMap: mutable.HashMap[String, Option[PsiJavaFile]] = mutable.HashMap.empty

  def isValidScalaFile(text: String, project: Project): Boolean = {
    if (text == null || project == null) false
    else createScalaFile(text, project).exists(isParsedCorrectly)
  }

  def isValidJavaFile(text: String, project: Project): Boolean = {
    if (text == null || project == null) false
    else createJavaFile(text, project).exists(isParsedCorrectly)
  }

  def isParsedCorrectly(file: PsiFile): Boolean = !file.depthFirst().exists(_.isInstanceOf[PsiErrorElement])

  def createScalaFile(text: String, project: Project): Option[ScalaFile] =
    scalaFileMap.getOrElseUpdate(text, new ScalaCodeFragment(project, text).asOptionOf[ScalaFile])

  def createJavaFile(text: String, project: Project): Option[PsiJavaFile] =
    javaFileMap.getOrElseUpdate(text,
      PsiFileFactory.getInstance(project).createFileFromText("Dummy.java", JavaFileType.INSTANCE, text).asOptionOf[PsiJavaFile])
}
