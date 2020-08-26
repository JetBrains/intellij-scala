package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina on 12/27/16.
  *
  * Try to add additional imports to temp java file, using [[ImportClassFix]]
  * @param javaFile - temp file from [[TextJavaCopyPastePostProcessor]] created from text
  * @param scope - javaFile creates with Project & Libraries scope, specify this parameter
  *                to filter additional imports by scope.
  */
class AdditionalImportsResolver(javaFile: PsiJavaFile, scope: GlobalSearchScope) {
  def addImports(): PsiJavaFile = {
    val project = javaFile.getProject

    def unresolvedRefs: Seq[PsiJavaCodeReferenceElement] = {
      PsiTreeUtil.collectElements(javaFile, (element: PsiElement) => {
        Option(element).exists {
          case javaref: PsiJavaCodeReferenceElement => javaref.resolve() == null
          case _ => false
        }
      }).collect { case javaRef: PsiJavaCodeReferenceElement => javaRef }
    }

    val failedToResolveNames = new ArrayBuffer[String]

    // TODO: support imports in current scope (add To already defined)
    val alreadyResolvedNames = new ArrayBuffer[String]

    def isClassInScope(clazz: PsiClass): Boolean =
      scope.contains(clazz.getContainingFile.getVirtualFile)

    def handleReference(reference: PsiJavaCodeReferenceElement, allImports: PsiImportList): Unit = {
      def createImportStatement(clazz: PsiClass): PsiImportStatement = {
        PsiElementFactory.getInstance(project).createImportStatement(clazz)
      }

      val refName = reference.getReferenceName
      if (!(failedToResolveNames.contains(refName) || alreadyResolvedNames.contains(refName))) {
        val importClassFix = new ImportClassFix(reference)
        val classesToImport = importClassFix.getClassesToImport.asScala.filter(isClassInScope)
        if (classesToImport.length == 1) {
          alreadyResolvedNames += refName
          allImports.add(createImportStatement(classesToImport.head))
        } else {
          failedToResolveNames += refName
        }
      }
    }

    unresolvedRefs.reverse.foreach(el => handleReference(el, javaFile.getImportList))

    javaFile
  }
}
