package org.jetbrains.plugins.scala.lang.psi.javaView {
  import com.intellij.extapi.psi.LightPsiFileBase
  import com.intellij.psi._
  import com.intellij.pom.java.LanguageLevel
  import com.intellij.lang.StdLanguages
  import com.intellij.openapi.fileTypes.StdFileTypes
  import com.intellij.util.ArrayUtil

  class ScJavaFileImpl(viewProvider : FileViewProvider) extends LightPsiFileBase(viewProvider, StdLanguages.JAVA) with PsiJavaFile {
    def getLanguageLevel = LanguageLevel.JDK_1_5

    def findImportReferenceTo (psiClass : PsiClass) = null

    def getImplicitlyImportedPackageReferences = PsiJavaCodeReferenceElement.EMPTY_ARRAY

    def getImplicitlyImportedPackages = ArrayUtil.EMPTY_STRING_ARRAY

    def getSingleClassImports(checkIncludes : Boolean) = PsiClass.EMPTY_ARRAY

    def getOnDemandImports(includeImplicit : Boolean, checkIncludes : Boolean) = PsiElement.EMPTY_ARRAY

    def getImportList = null

    def getPackageStatement = null

    def importClass(psiClass : PsiClass) = false

    //todo
    def getPackageName = null

    //todo
    def getClasses = PsiClass.EMPTY_ARRAY

    def getChildren = getClasses.asInstanceOf[Array[PsiElement]]

    def copyLight (newFileViewProvider : FileViewProvider) = new ScJavaFileImpl(newFileViewProvider)

    def clearCaches = {}

    def getFileType = StdFileTypes.JAVA
  }
}