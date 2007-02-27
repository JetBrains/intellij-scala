/**
 * @author ven
 */
package org.jetbrains.plugins.scala.lang.psi.javaView
import com.intellij.extapi.psi.LightPsiFileBase
import com.intellij.psi._
import com.intellij.pom.java.LanguageLevel
import com.intellij.lang.StdLanguages
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.impl.top._
import java.io.Serializable

/**
 *  Java toplevel view of the scala file
 *  @see PsiJavaFile
 */
class ScJavaFile(viewProvider: FileViewProvider) extends LightPsiFileBase(viewProvider, StdLanguages.JAVA) with PsiJavaFile
with Serializable{
  def getLanguageLevel = LanguageLevel.JDK_1_5

  def findImportReferenceTo(psiClass: PsiClass) = null

  def getImplicitlyImportedPackageReferences = PsiJavaCodeReferenceElement.EMPTY_ARRAY

  def getImplicitlyImportedPackages = ArrayUtil.EMPTY_STRING_ARRAY

  def getSingleClassImports(checkIncludes: Boolean) = PsiClass.EMPTY_ARRAY

  def getOnDemandImports(includeImplicit: Boolean, checkIncludes: Boolean) = PsiElement.EMPTY_ARRAY

  def getImportList = null

  def getPackageStatement = null

  def importClass(psiClass: PsiClass) = false

  //todo
  def getPackageName = ""

  override def getClasses = {
    val scFile = viewProvider.getPsi(ScalaFileType.SCALA_FILE_TYPE.getLanguage).asInstanceOf[ScalaFile]
    scFile.getTmplDefs.map(c => new ScJavaClass(c, this)).toArray[PsiClass]
  }

  def getChildren = getClasses.asInstanceOf[Array[PsiElement]]

  def copyLight(newFileViewProvider: FileViewProvider) = new ScJavaFile(newFileViewProvider)

  def clearCaches = {
    //do not clear caches here, this method is not called
  }

  def getFileType = StdFileTypes.JAVA

  override def findElementAt(offset: Int): PsiElement = {
    for (val child <- getChildren) {
      val textRange = child.getTextRange
      if (textRange.contains(offset)) {
        return child.findElementAt(offset - textRange.getStartOffset)
      }
    }
    if (getTextRange.contains(offset))  this else null
  }
}
