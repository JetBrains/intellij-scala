package org.jetbrains.plugins.scala
package annotator
package importsTracker


import collection.Set
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.usages.ImportUsed
import com.intellij.codeInsight.daemon.impl.RefCountHolder
import java.lang.String
import com.intellij.psi._
import impl.light.LightElement
import collection.mutable.{ArrayBuffer, HashMap}

/**
 * @author Alexander Podkhalyuzin
 */


/**
 * Main idea for this class is to use Java class RefCountHolder.
 * todo: we need own RefCountHolder, this solution is bad
 * We can give to RefCountHolder our fake Psi with inner real psi.
 * So usage is same.
 * todo: possible solution is to make something common
 */
class ImportTracker {
  private class FakeJavaResolveResult(i: ImportUsed) extends JavaResolveResult {
    //this methods are important
    def getCurrentFileResolveScope: PsiElement = FakePsiImportStatementBase(i)

    //this methods are redundant
    def isStaticsScopeCorrect: Boolean = false
    def isAccessible: Boolean = false
    def isPackagePrefixPackageReference: Boolean = false
    def getSubstitutor: PsiSubstitutor = null
    def isValidResult: Boolean = false
    def getElement: PsiElement = null
  }

  private case class FakePsiImportStatementBase(i: ImportUsed) extends LightElement(i.e.getManager, i.e.getLanguage)
          with PsiImportStatementBase {  //nohing is matter for this fake psi element
    override def toString: String = "FakePsiImportStatementBase " + i
    def resolve: PsiElement = null
    def getImportReference: PsiJavaCodeReferenceElement = null
    def isOnDemand: Boolean = false
    def copy: PsiElement = null //todo:?
    def accept(visitor: PsiElementVisitor): Unit = {}
    def getText: String = "FakePsiImportStatementBase " + i
  }

  def registerUsedImports(file: ScalaFile, used: Set[ImportUsed]) {
    val refHolder = RefCountHolder.getInstance(file)
    for (imp <- used) {
      refHolder.registerReference(new FakePsiJavaReference, new FakeJavaResolveResult(imp))
    }
  }

  def getUnusedImport(file: ScalaFile): Array[ImportUsed] = {
    val buff = new ArrayBuffer[ImportUsed]
    val iter = file.getAllImportUsed.iterator
    val refHolder = RefCountHolder.getInstance(file)
    val runnable = new Runnable {
      def run {
        while (!iter.isEmpty) {
          val used = iter.next
          if (refHolder.isRedundant(FakePsiImportStatementBase(used))) buff += used
        }
      }
    }
    refHolder.retrieveUnusedReferencesInfo(runnable)
    return buff.toArray
  }
}

object ImportTracker {
  def getInstance(project: Project): ImportTracker = {
    ServiceManager.getService(project, classOf[ImportTracker])
  }
}