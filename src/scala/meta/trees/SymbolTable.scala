package scala.meta.trees

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.{impl, api => p, types => ptype}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.language.postfixOps
import scala.meta.collections._
import scala.meta.internal.{semantic => h}
import scala.meta.trees.error._
import scala.{meta => m}

trait SymbolTable {
  self: TreeConverter =>

  private val symbolCache = TwoWayCache[PsiElement, h.Symbol]()

  def localSymbolDelim = "*"

  def isLocal(elem: PsiElement): Boolean = {
    elem match {
      case e: ScTypeDefinition => e.isLocal
      case e: ScBindingPattern if e.containingClass == null => true
      case pp: params.ScParameter => true
      case tp: ScTypeParam => true
      case _ => false
    }
  }

  def fqnameToSymbol(fqName: String, toDrop: Int = 1): h.Symbol = {
    fqName
      .split('.')
      .dropRight(toDrop)
      .foldLeft(h.Symbol.RootPackage.asInstanceOf[h.Symbol])((parent, name) =>
        h.Symbol.Global(parent, h.ScalaSig.Term(name), h.BinarySig.None))
  }

  def symbolToFqname(sym: h.Symbol): String = {
    def rec(s: h.Symbol): String = {
      s match {
        case h.Symbol.RootPackage => ""
        case h.Symbol.EmptyPackage => ""
        case h.Symbol.Global(parent, name, _) => rec(parent) + "." + name
      }
    }
    rec(sym)
  }

  def ownerSymbol(elem: PsiElement): h.Symbol = {
    elem match {
      case mm: ScMember =>
        if (mm.containingClass == null) h.Symbol.EmptyPackage else toSymbol(mm.containingClass)
      case bp: ScBindingPattern =>
        if (bp.containingClass == null) h.Symbol.EmptyPackage else toSymbol(bp.containingClass)
      case pc: PsiMember =>
        if (pc.getContainingClass == null) h.Symbol.EmptyPackage else toSymbol(pc.getContainingClass)
      case other => other ?!
    }
  }

  // used for generating symbols when no direct access to parent is avaliable(e.g. ScType)
  def toSymbolWtihParent(name: String, parent: PsiElement, signature: h.ScalaSig) = {
    h.Symbol.Global(toSymbol(parent), signature, h.BinarySig.None)
  }

  def toSymbol(res: ResolveResult): h.Symbol = {
    res match {
      case ScalaResolveResult(elem: PsiNamedElement, subst) =>
        toSymbol(elem)
      case rr:ResolveResult if rr.isValidResult =>
        toSymbol(rr.getElement)
      case _ => die(s"Unresolved: $res")
    }
  }

  def toSymbol(elem: PsiElement): h.Symbol = {
    ProgressManager.checkCanceled()
    def convert = elem match {
      case _ if isLocal(elem) =>
        toLocalSymbol(elem)
      case sc: impl.toplevel.synthetic.ScSyntheticClass =>
        h.Symbol.Global(fqnameToSymbol(sc.getQualifiedName), h.ScalaSig.Type(sc.className), h.BinarySig.None)
      case td: ScTypeDefinition if !td.qualifiedName.contains(".") => // empty package defn
        h.Symbol.Global(h.Symbol.EmptyPackage, h.ScalaSig.Type(td.name), h.BinarySig.None)
      case td: ScTemplateDefinition =>
        h.Symbol.Global(fqnameToSymbol(td.qualifiedName), h.ScalaSig.Type(td.name), h.BinarySig.None)
      case td: ScFieldId =>
        val owner = td.nameContext match {
          case vd: ScValueDeclaration => ownerSymbol(vd)
          case vd: ScVariableDeclaration => ownerSymbol(vd)
          case other => other ?!
        }
        h.Symbol.Global(owner, h.ScalaSig.Term(td.name), h.BinarySig.None)
      case td: ScFunction =>
        // TODO: meta trees don't resolve unapply methods(or do they?)
        if (td.name == "unapply")
          toSymbol(td.containingClass)
        else {
          val jvmsig = DebuggerUtil.getFunctionJVMSignature(td).getName(null)
          h.Symbol.Global(ownerSymbol(td), h.ScalaSig.Method(td.name, jvmsig), h.BinarySig.None)
        }
      case pc: ScPackaging =>
        toSymbol(pc.reference.get.bind().get.element)
      case cr: ScStableCodeReferenceElement =>
        toSymbol(cr.resolve())
      case ta: ScTypeAlias =>
        h.Symbol.Global(ownerSymbol(ta), h.ScalaSig.Type(ta.name), h.BinarySig.None)
      case bp: ScBindingPattern =>
        h.Symbol.Global(ownerSymbol(bp), h.ScalaSig.Term(bp.name), h.BinarySig.None)
      // Java stuff starts here
      case pc: PsiClass =>
        h.Symbol.Global(ownerSymbol(pc), h.ScalaSig.Type(pc.getName), h.BinarySig.None)
      case pm: PsiMethod =>
        val jvmsig = JVMNameUtil.getJVMSignature(pm).getName(null)
        h.Symbol.Global(ownerSymbol(pm), h.ScalaSig.Method(pm.getName, jvmsig), h.BinarySig.None)
      case pp: PsiPackage if pp.getName == null =>
        h.Symbol.RootPackage
      case pc: PsiPackage =>
        h.Symbol.Global(toSymbol(pc.getParentPackage), h.ScalaSig.Term(pc.getName), h.BinarySig.None)
      case _ => elem ?!
    }
    symbolCache.getOrElseUpdate(elem, convert)
  }

  def toLocalSymbol(elem: PsiElement): h.Symbol = {
      @tailrec
      def getFile(elem: PsiElement): PsiFile =  elem.getContainingFile match {
        case _: DummyHolder => getFile(elem.getParent)
        case _ => elem.getContainingFile
      }
      val url = try {
        getFile(elem).getVirtualFile.getUrl
      } catch {
        case _: NullPointerException => "UNRESOLVED"
      }
      h.Symbol.Local(url + localSymbolDelim + elem.getTextOffset)
  }

  def fromSymbol(sym: h.Symbol): PsiElement = {
    def getFqName(sym: h.Symbol): (String, Option[String]) = sym match {
      case h.Symbol.Global(owner, name, signature) => unreachable("FIXME: Backward symbol lookup")
      case other => unreachable("can't get fqn of non-global symbol")
    }

    def convert: PsiElement = sym match {
      case h.Symbol.Local(id) =>
        val url::pos = id.split(localSymbolDelim).toList
        findFileByPath(url).findElementAt(pos.head.toInt)
      case h.Symbol.RootPackage => new PsiPackageImpl(PsiManager.getInstance(getCurrentProject), "")
      case h.Symbol.EmptyPackage => new PsiPackageImpl(PsiManager.getInstance(getCurrentProject), "")
      case h.Symbol.Global(owner, name, signature) =>
        getFqName(sym) match {
          case (fqn, Some(jvmSig)) =>
            val clazz = ScalaPsiManager.instance(getCurrentProject).getCachedClass(
              GlobalSearchScope.projectScope(getCurrentProject), fqn.split('.').drop(1).mkString(".")
            )
            clazz match {
              case sc: ScTemplateDefinition =>
//                sc.findMethodBySignature()
                sc ???
              case pc: PsiClass =>
                pc ???
            }
          case (fqn, None) =>
            ScalaPsiManager.instance(getCurrentProject).getCachedClass(
              GlobalSearchScope.projectScope(getCurrentProject), fqn.split('.').drop(1).mkString(".")
            ).get
        }
    }

    symbolCache.getOrElseUpdate(sym, convert)
  }
}
