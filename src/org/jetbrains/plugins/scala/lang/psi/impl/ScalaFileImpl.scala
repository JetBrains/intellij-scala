package org.jetbrains.plugins.scala.lang.psi.impl


import api.expr.ScExpression
import api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}


import com.intellij.openapi.roots.{OrderEntry, ProjectRootManager, OrderRootType}
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.file.PsiPackageImpl
import lexer.ScalaTokenTypes
import stubs.ScFileStub
import _root_.com.intellij.extapi.psi.{PsiFileBase}
import api.ScalaFile
import com.intellij.psi.stubs.StubElement
import com.intellij.util.IncorrectOperationException
import api.toplevel.typedef.ScTypeDefinition
import api.toplevel.imports.ScImportStmt
import com.intellij.lang.Language
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import decompiler.CompiledFileAdjuster
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import psi.api.toplevel._
import psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._

import _root_.scala.collection.mutable._


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


  def isScriptFile: Boolean = {
    val stub = getStub
    if (stub == null) {
      val res = getUserData(ScalaFileImpl.SCRIPT_KEY)
      def calcResult(): Boolean = {
        for (n <- getNode.getChildren(null); child = n.getPsi) {
          child match {
            case _: ScPackageStatement | _: ScPackaging => return false
            case _: ScValue | _: ScVariable | _: ScFunction | _: ScExpression | _: ScTypeAlias => return true
            case _ => if (n.getElementType == ScalaTokenTypes.tSH_COMMENT) return true
          }
        }
        return false
      }
      if (res == null) {
        val b = calcResult
        putUserData(ScalaFileImpl.SCRIPT_KEY, new java.lang.Boolean(b))
        return b
      }
      return java.lang.Boolean.TRUE == res
    } else {
      stub.isScript
    }
  }

  def setPackageName(name: String) {
    //todo
  }

  override def getStub: ScFileStub = super[PsiFileBase].getStub.asInstanceOf[ScFileStub]

  def getPackagings = findChildrenByClass(classOf[ScPackaging])

  def getPackageName: String =
    packageStatement match {
      case None => ""
      case Some(stat) => stat.getPackageName
    }


  def packageStatement = findChild(classOf[ScPackageStatement])

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
        var curr = JavaPsiFacade.getInstance(getProject).findPackage(getPackageName)
        while (curr != null) {
          if (!curr.processDeclarations(processor, state, null, place)) return false
          curr = curr.getParentPackage
        }
      }
    }

    for (implObj <- ImplicitlyImported.objects) {
      val clazz = JavaPsiFacade.getInstance(getProject).findClass(implObj, getResolveScope)
      if (clazz != null && !clazz.processDeclarations(processor, state, null, place)) return false
    }

    import toplevel.synthetic.SyntheticClasses

    for (synth <- SyntheticClasses.get(getProject).getAll) {
      if (!processor.execute(synth, state)) return false;
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
  val objects = Array("scala.Predef")
}

private object ScalaFileImpl {
  val SCRIPT_KEY = new Key[java.lang.Boolean]("Is Script Key")
}
