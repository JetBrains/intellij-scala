package org.jetbrains.plugins.scala.debugger

import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.sun.jdi.ReferenceType
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

object TopLevelMembers {
  private val classSuffix = "$package$"

  def isSyntheticClassForTopLevelMembers(refType: ReferenceType): Boolean = refType.name.endsWith(classSuffix)

  def topLevelMemberClassName(file: PsiFile, packaging: Option[ScPackaging]): Option[String] =
    for {
      vf <- file.getVirtualFile.toOption
      packageName = packaging.fold("")(_.fullPackageName)
    } yield {
      val path =
        if (packageName.isEmpty) vf.getNameWithoutExtension
        else packageName + "." + vf.getNameWithoutExtension
      path + classSuffix
    }

  def findFileWithTopLevelMembers(scope: ElementScope, originalQName: String): Option[PsiFile] = {
    val path = originalQName.stripSuffix(classSuffix)
    val (packageName, fileName) = path.lastIndexOf('.') match {
      case lastDot if lastDot > 0 => (path.substring(0, lastDot), path.substring(lastDot + 1) + ".scala")
      case _ => ("", path + ".scala")
    }
    val files = FilenameIndex.getFilesByName(scope.project, fileName, scope.scope)

    val condition: ScalaFile => Boolean = (file: ScalaFile) =>
      if (packageName.isEmpty) file.firstPackaging.isEmpty
      else file.firstPackaging.map(_.fullPackageName).contains(packageName)

    files.toSeq.filterByType[ScalaFile].find(condition)
  }
}
