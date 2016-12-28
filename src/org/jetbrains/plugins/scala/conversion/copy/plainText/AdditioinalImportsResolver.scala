package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.psi.util.{PsiElementFilter, PsiTreeUtil}
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

import scala.collection.mutable.ArrayBuffer

/**
  * Created by user on 12/27/16.
  */
class AdditioinalImportsResolver(javaFile: PsiJavaFile) {
  def addImports(): PsiJavaFile = {
    val project = javaFile.getProject

    def unresolvedRefs: Seq[PsiElement] = {
      PsiTreeUtil.collectElements(javaFile, new PsiElementFilter {
        override def isAccepted(element: PsiElement): Boolean = {
          element != null &&
            element.isInstanceOf[PsiQualifiedReference] &&
            element.asInstanceOf[PsiQualifiedReference].resolve() == null
        }
      })
    }

    val failedToResolveNames = new ArrayBuffer[String]

    // TODO: support imports in current scope (add To already defined)
    val alreadyResolvedNames = new ArrayBuffer[String]

    def handleReference(element: PsiElement, allImports: PsiImportList): Unit ={
      val reference = element.asInstanceOf[PsiQualifiedReference]

      def isValidClassToImport(clazz: PsiClass): Boolean = {
        clazz != null &&
          clazz.getQualifiedName != null &&
          ResolveUtils.isAccessible(clazz, element) &&
          !JavaCompletionUtil.isInExcludedPackage(clazz, false) &&
          ScalaImportTypeFix.notInner(clazz, element)
      }

      def classes: Seq[PsiClass] = {
        ScalaPsiManager.instance(project).getClassesByName(reference.getReferenceName, javaFile.getResolveScope)
      }

      def createImportStatement(clazz: PsiClass): PsiImportStatement = {
        PsiElementFactory.SERVICE.getInstance(project).createImportStatement(clazz)
      }

      val refName = reference.getReferenceName
      if (!(failedToResolveNames.contains(refName) || alreadyResolvedNames.contains(refName))) {
        // TODO: clevere
        val valid = classes.filter(isValidClassToImport)
        if (valid.length == 1) {
          alreadyResolvedNames += refName
          allImports.add(createImportStatement(valid.head))
        } else {
          failedToResolveNames += refName
        }
      }
    }

    unresolvedRefs.foreach(el => handleReference(el, javaFile.getImportList))

    javaFile
  }
}
