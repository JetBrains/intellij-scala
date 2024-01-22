package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import com.sun.jdi.ReferenceType
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.annotation.nowarn

private[scala] object TopLevelMembers {
  private val classSuffix = "$package$"

  def isSyntheticClassForTopLevelMembers(refType: ReferenceType): Boolean = refType.name.endsWith(classSuffix)

  def topLevelMemberClassName(file: PsiFile, packaging: Option[ScPackaging]): String = {
    val fileName    = ScalaNamesUtil.toJavaName(FileUtilRt.getNameWithoutExtension(file.getName))
    val packageName = packaging.fold("")(_.fullPackageName)

    val path =
      if (packageName.isEmpty) fileName
      else packageName + "." + fileName

    path + classSuffix
  }

  def topLevelMemberClassName(element: PsiElement): String =
    topLevelMemberClassName(element.getContainingFile, PsiTreeUtil.getParentOfType(element, classOf[ScPackaging]).toOption)

  def hasTopLevelMembers(elem: PsiElement): Boolean = {
    val members = elem match {
      case p: ScPackaging                           => p.immediateMembers
      case f: ScalaFile if f.firstPackaging.isEmpty => f.members
      case _                                        => Seq.empty
    }
    members.exists(!_.is[ScTypeDefinition])
  }

  def findFileWithTopLevelMembers(
    project:       Project,
    scope:         GlobalSearchScope,
    originalQName: String,
    suffix:        String = classSuffix
  ): Option[ScalaFile] = {
    val path = originalQName.stripSuffix(suffix)
    val (packageName, fileName) = path.lastIndexOf('.') match {
      case lastDot if lastDot > 0 => (path.substring(0, lastDot), path.substring(lastDot + 1) + ".scala")
      case _ => ("", path + ".scala")
    }
    @nowarn("cat=deprecation")
    val files = FilenameIndex.getFilesByName(project, fileName, scope)

    val condition: ScalaFile => Boolean = (file: ScalaFile) =>
      if (packageName.isEmpty) file.firstPackaging.isEmpty
      else file.firstPackaging.map(_.fullPackageName).contains(packageName)

    files.toSeq.filterByType[ScalaFile].find(condition)
  }
}
