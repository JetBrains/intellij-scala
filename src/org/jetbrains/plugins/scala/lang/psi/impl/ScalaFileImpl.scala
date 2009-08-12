package org.jetbrains.plugins.scala
package lang
package psi
package impl


import api.base.{ScStableCodeReferenceElement}
import api.expr.ScExpression
import api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import caches.{ScalaCachesManager, CachesUtil}
import com.intellij.openapi.roots.{OrderEntry, ProjectRootManager, OrderRootType}
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.{ArrayFactory}
import lexer.ScalaTokenTypes
import stubs.ScFileStub
import _root_.com.intellij.extapi.psi.{PsiFileBase}
import api.ScalaFile
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import decompiler.CompiledFileAdjuster
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import psi.api.toplevel.packaging._

class ScalaFileImpl(viewProvider: FileViewProvider)
        extends PsiFileBase(viewProvider, ScalaFileType.SCALA_FILE_TYPE.getLanguage())
                with ScalaFile with ScImportsHolder with ScDeclarationSequenceHolder with CompiledFileAdjuster {
  override def getViewProvider = viewProvider

  override def getFileType = ScalaFileType.SCALA_FILE_TYPE

  override def toString = "ScalaFile"

  def isCompiled = {
    val stub = getStub
    if (stub != null) stub.isCompiled else compiled
  }

  def sourceName = if (isCompiled) sourceFileName else ""

  override def getNavigationElement: PsiElement = {
    if (!isCompiled) this
    else {
      val pName = getPackageName
      val sourceFile = sourceName
      val relPath = if (pName.length == 0) sourceFile else pName.replace(".", "/") + "/" + sourceFile

      val vFile = getContainingFile.getVirtualFile
      val index = ProjectRootManager.getInstance(getProject).getFileIndex
      val entries = index.getOrderEntriesForFile(vFile).toArray(OrderEntry.EMPTY_ARRAY)
      for (entry <- entries) {
        val files = entry.getFiles(OrderRootType.SOURCES)
        for (file <- files) {
          val source = file.findFileByRelativePath(relPath)
          if (source != null) {
            val psiSource = getManager.findFile(source)
            return psiSource match {
              case o: PsiClassOwner => o
              case _ => this
            }
          }
        }
      }
      this
    }
  }


  private def isScriptFileImpl: Boolean = {
    val stub = getStub
    if (stub == null) {
      for (n <- getNode.getChildren(null); child = n.getPsi) {
        child match {
          case _: ScPackaging => return false
          case _: ScValue | _: ScVariable | _: ScFunction | _: ScExpression | _: ScTypeAlias => return true
          case _ => if (n.getElementType == ScalaTokenTypes.tSH_COMMENT) return true
        }
      }
      return false
    } else {
      stub.isScript
    }
  }

  def isScriptFile: Boolean = isScriptFile(true)
  def isScriptFile(withCashing: Boolean): Boolean = {
    if (!withCashing) return isScriptFileImpl
    import CachesUtil._
    get[ScalaFileImpl, java.lang.Boolean](this, SCRIPT_KEY,
      new MyProvider(this, {sf: ScalaFileImpl => new java.lang.Boolean(sf.isScriptFileImpl)})(this)) == java.lang.Boolean.TRUE
  }

  def setPackageName(name: String) {
    //todo implement with packagings
  }

  override def getStub: ScFileStub = super[PsiFileBase].getStub.asInstanceOf[ScFileStub]

  def getPackagings: Array[ScPackaging] = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.PACKAGING, new ArrayFactory[ScPackaging] {
        def create(count: Int): Array[ScPackaging] = new Array[ScPackaging](count)
      })
    } else findChildrenByClass(classOf[ScPackaging])
  }

  def getPackageName: String = ""

  override def getClasses = {
    if (!isScriptFile) {
      val stub = getStub
      if (stub != null) {
        stub.getClasses
      } else {
        typeDefinitions.map(t => t: PsiClass)
      }
    } else PsiClass.EMPTY_ARRAY
  }

  def icon = Icons.FILE_TYPE_LOGO

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    if (!super[ScDeclarationSequenceHolder].processDeclarations(processor,
      state, lastParent, place)) return false

    if (!super[ScImportsHolder].processDeclarations(processor,
      state, lastParent, place)) return false

    place match {
      case ref: ScStableCodeReferenceElement if ref.refName == "_root_" => {
        val top = JavaPsiFacade.getInstance(getProject()).findPackage("")
        if (top != null && !processor.execute(top, state.put(ResolverEnv.nameKey, "_root_"))) return false
        state.put(ResolverEnv.nameKey, null)
      }
      case _ => {
        //todo: this is redundant pName now always ""
        val pName = getPackageName

        // Treat package object first
        val manager = ScalaCachesManager.getInstance(getProject)
        val cache = manager.getNamesCache

        val obj = cache.getPackageObjectByName(pName, GlobalSearchScope.allScope(getProject))
        if (obj != null) {
          if (!obj.processDeclarations(processor, state, null, place)) return false
        }

        var current = JavaPsiFacade.getInstance(getProject).findPackage(pName)
        while (current != null) {
          if (!current.processDeclarations(processor, state, null, place)) return false
          current = current.getParentPackage

          // Treat parent package object
          if (current != null) {
            val parentObj = cache.getPackageObjectByName(current.getQualifiedName, GlobalSearchScope.allScope(getProject))
            if (parentObj != null) {
              if (!parentObj.processDeclarations(processor, state, null, place)) return false
            }
          }
        }
      }
    }

    for (implObj <- ImplicitlyImported.objects) {
      val clazz = JavaPsiFacade.getInstance(getProject).findClass(implObj, getResolveScope)
      if (clazz != null && !clazz.processDeclarations(processor, state, null, place)) return false
    }

    import toplevel.synthetic.SyntheticClasses

    for (synth <- SyntheticClasses.get(getProject).getAll) {
      if (!processor.execute(synth, state)) return false
    }

    if (isScriptFile) {
      for (syntheticValue <- SyntheticClasses.get(getProject).getScriptSyntheticValues) {
        if (!processor.execute(syntheticValue, state)) return false
      }
    }

    for (implP <- ImplicitlyImported.packages) {
      val pack = JavaPsiFacade.getInstance(getProject()).findPackage(implP)
      if (pack != null && !pack.processDeclarations(processor, state, null, place)) return false
    }

    true
  }


  override def findReferenceAt(offset: Int): PsiReference = super.findReferenceAt(offset)

  private var context: PsiElement = null


  override def getContext: PsiElement = {
    if (context != null) context
    else super.getContext
  }

  def setContext(context: PsiElement): Unit = this.context = context
}

object ImplicitlyImported {
  val packages = Array("scala", "java.lang")
  val objects = Array("scala.Predef", "scala" /* package object*/ )
}

private object ScalaFileImpl {
  val SCRIPT_KEY = new Key[java.lang.Boolean]("Is Script Key")
}
