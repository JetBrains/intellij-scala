package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix
import com.intellij.psi._
import com.intellij.psi.util.{PsiElementFilter, PsiTreeUtil}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina on 12/27/16.
  */
class AdditioinalImportsResolver(javaFile: PsiJavaFile) {
  def addImports(): PsiJavaFile = {
    val project = javaFile.getProject

    def unresolvedRefs: Seq[PsiJavaCodeReferenceElement] = {
      PsiTreeUtil.collectElements(javaFile, new PsiElementFilter {
        override def isAccepted(element: PsiElement): Boolean = {
          Option(element).exists {
            case javaref: PsiJavaCodeReferenceElement => javaref.resolve() == null
            case _ => false
          }
        }
      }).collect { case javaRef: PsiJavaCodeReferenceElement => javaRef }
    }

    val failedToResolveNames = new ArrayBuffer[String]

    // TODO: support imports in current scope (add To already defined)
    val alreadyResolvedNames = new ArrayBuffer[String]

    def handleReference(reference: PsiJavaCodeReferenceElement, allImports: PsiImportList): Unit = {
      def createImportStatement(clazz: PsiClass): PsiImportStatement = {
        PsiElementFactory.SERVICE.getInstance(project).createImportStatement(clazz)
      }

      val refName = reference.getReferenceName
      if (!(failedToResolveNames.contains(refName) || alreadyResolvedNames.contains(refName))) {
        val importClassFix = new ImportClassFix(reference)
        val calssesToImport = importClassFix.getClassesToImport
        if (calssesToImport.size() == 1) {
          alreadyResolvedNames += refName
          allImports.add(createImportStatement(calssesToImport.get(0)))
        } else {
          failedToResolveNames += refName
        }
      }
    }

    unresolvedRefs.foreach(el => handleReference(el, javaFile.getImportList))

    javaFile
  }
}
