package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.{GlobalSearchScope, ProjectScope, PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.xml.XmlToken
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{Method, SearchMethodResult}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

private[cheapRefSearch] final class IJExtensionPointImplementationSearch(override val shouldProcess: ShouldProcess)
  extends Method {

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    val helper = PsiSearchHelper.getInstance(ctx.element.getProject)

    var maybeUsage: Option[ElementUsage] = None

    val processor = new TextOccurenceProcessor {
      override def execute(e2: PsiElement, offsetInElement: Int): Boolean = e2 match {
        case _: XmlToken =>
          maybeUsage = Some(ElementUsageWithUnknownReference)
          false
        case _ => true
      }
    }

    val globalScope = new GlobalSearchScope(ctx.element.getProject) {
      override def contains(file: VirtualFile): Boolean =
        file.getFileType.is[XmlFileType] && file.getExtension.equalsIgnoreCase("xml")

      override def isSearchInModuleContent(aModule: module.Module): Boolean = false

      override def isSearchInLibraries: Boolean = false
    }

    val finalSearchScope = globalScope.intersectWith(ProjectScope.getContentScope(ctx.element.getProject))

    val stringToSearch = ctx.element.asOptionOf[ScClass].map(_.qualifiedName)

    stringToSearch.foreach(helper.processElementsWithWord(processor, finalSearchScope, _, UsageSearchContext.IN_FOREIGN_LANGUAGES, true))

    new SearchMethodResult(maybeUsage.toSeq, false)
  }
}
