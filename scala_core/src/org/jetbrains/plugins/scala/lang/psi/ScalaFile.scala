package org.jetbrains.plugins.scala.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.Nullable
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import psi.api.toplevel._
import psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import com.intellij.pom.java.LanguageLevel
import com.intellij.lang.StdLanguages
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.util.ArrayUtil


class ScalaFile (viewProvider: FileViewProvider) extends PsiFileBase (viewProvider, ScalaFileType.SCALA_FILE_TYPE.getLanguage())
with ScalaPsiElement with ScTypeDefinitionOwner with PsiClassOwner {

  override def getViewProvider = viewProvider
  override def getFileType = ScalaFileType.SCALA_FILE_TYPE
  override def toString = "ScalaFile"


  def getUpperDefs = childrenOfType[ScalaPsiElementImpl] (TokenSets.TMPL_DEF_BIT_SET)

  def setPackageName(name: String) = {}

  def getPackagings: Iterable [ScPackaging] = childrenOfType[ScPackaging] (TokenSets.PACKAGING_BIT_SET)

  def getPackageName = {
    val p = getPackageStatement
    if (p != null) p.getPackageName else ""
  }
  
  def getPackageStatement = findChildByClass(classOf[ScPackageStatement])

  override def getClasses = getTypeDefinitionsArray.map((t: ScTypeDefinition) => t.asInstanceOf[PsiClass])


  /*
    override def processDeclarations(processor: PsiScopeProcessor,
            substitutor: PsiSubstitutor,
            lastParent: PsiElement,
            place: PsiElement): Boolean = {

      import org.jetbrains.plugins.scala.lang.resolve.processors._

      if (processor.isInstanceOf[ScalaClassResolveProcessor]) {
          this.canBeObject = processor.asInstanceOf[ScalaClassResolveProcessor].canBeObject
          this.offset = processor.asInstanceOf[ScalaClassResolveProcessor].offset
        getClazz(getUpperDefs, processor, substitutor)
      } else true
    }
  */


}
