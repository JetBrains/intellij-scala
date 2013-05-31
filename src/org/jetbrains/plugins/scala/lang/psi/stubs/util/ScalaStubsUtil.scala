package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package util


import index.{ScSelfTypeInheritorsIndex, ScDirectInheritorsIndex}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StubInputStream, StubOutputStream, StubIndex}
import com.intellij.psi.{PsiMethod, PsiElement, PsiClass}
import elements.ScTemplateDefinitionElementType
import psi.impl.toplevel.templates.ScExtendsBlockImpl
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.openapi.diagnostic.Logger
import api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import collection.mutable.ArrayBuffer
import api.expr.ScAnnotation
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilCore}
import extensions.toPsiNamedElementExt
import psi.impl.base.types.ScSelfTypeElementImpl
import psi.types.result.{Success, TypingContext}
import psi.types.{ScCompoundType, ScType}
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.Processor
import api.statements.{ScVariable, ScValue}
import api.toplevel.packaging.ScPackageContainer
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFileStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaStubsUtil {
  def getClassInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null) return Seq.empty
    val inheritors = new ArrayBuffer[ScTemplateDefinition]
    val iterator: java.util.Iterator[PsiElement] =
      StubIndex.getInstance().get(ScDirectInheritorsIndex.KEY, name, clazz.getProject, scope).iterator.
              asInstanceOf[java.util.Iterator[PsiElement]]
    while (iterator.hasNext) {
      val extendsBlock: PsiElement = iterator.next
      if (checkPsiForExtendsBlock(extendsBlock)) {
        val stub = extendsBlock.asInstanceOf[ScExtendsBlockImpl].getStub
        if (stub != null) {
          if (stub.getParentStub.getStubType.isInstanceOf[ScTemplateDefinitionElementType[_ <: ScTemplateDefinition]]) {
            inheritors += stub.getParentStub.getPsi.asInstanceOf[ScTemplateDefinition]
          }
        }
        else {
          extendsBlock.getParent match {
            case tp: ScTemplateDefinition => inheritors += tp
            case _ =>
          }
        }
      }
    }
    inheritors.toSeq
  }

  def getSelfTypeInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null) return Seq.empty
    val inheritors = new ArrayBuffer[ScTemplateDefinition]
    def processClass(inheritedClazz: PsiClass) {
      val iterator: java.util.Iterator[PsiElement] =
        StubIndex.getInstance().get(ScSelfTypeInheritorsIndex.KEY, name, inheritedClazz.getProject, scope).iterator.
          asInstanceOf[java.util.Iterator[PsiElement]]
      while (iterator.hasNext) {
        val elem: PsiElement = iterator.next
        if (checkPsiForSelfTypeElement(elem)) {
          val selfTypeElement = elem.asInstanceOf[ScSelfTypeElementImpl]
          selfTypeElement.typeElement match {
            case Some(typeElement) =>
              typeElement.getType(TypingContext.empty) match {
                case Success(tp, _) =>
                  def checkTp(tp: ScType): Boolean = {
                    tp match {
                      case c: ScCompoundType =>
                        c.components.exists(checkTp(_))
                      case _ =>
                        ScType.extractClass(tp, Some(inheritedClazz.getProject)) match {
                          case Some(otherClazz) =>
                            if (otherClazz == inheritedClazz) return true
                          case _ =>
                        }
                    }
                    false
                  }
                  if (checkTp(tp)) {
                    val clazz = PsiTreeUtil.getContextOfType(selfTypeElement, classOf[ScTemplateDefinition])
                    if (clazz != null) inheritors += clazz
                  }
                case _ =>
              }
            case _ =>
          }
        }
      }
    }
    processClass(clazz)
    ClassInheritorsSearch.search(clazz, scope, true).forEach(new Processor[PsiClass] {
      def process(t: PsiClass) = {
        processClass(t)
        true
      }
    })
    inheritors.toSeq
  }

  def checkPsiForExtendsBlock(element: PsiElement): Boolean = {
    element match {
      case x: ScExtendsBlockImpl => true
      case _ => {
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non ScExtendsBlock in ScExtendsBlock list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
      }
    }
  }

  def checkPsiForSelfTypeElement(element: PsiElement): Boolean = {
    element match {
      case x: ScSelfTypeElementImpl => true
      case _ => {
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non ScSelfTypeElement in ScSelfTypeElement list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
      }
    }
  }

  def checkPsiForAnnotation(element: PsiElement): Boolean = {
    element match {
      case x: ScAnnotation => true
      case _ => {
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non ScAnnotation in ScAnnotation list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
      }
    }
  }

  def checkPsiForClass(element: PsiElement): Boolean = {
    element match {
      case x: PsiClass => true
      case _ => {
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non PsiClass in PsiClass list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
      }
    }
  }

  def checkPsiForValue(element: PsiElement): Boolean = {
    element match {
      case x: ScValue => true
      case _ => {
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non ScValue in ScValue list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
      }
    }
  }

  def checkPsiForVariable(element: PsiElement): Boolean = {
    element match {
      case x: ScVariable => true
      case _ => {
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non ScVariable in ScVariable list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
      }
    }
  }

  def checkPsiForPsiMethod(element: PsiElement): Boolean = {
    element match {
      case x: PsiMethod => true
      case _ =>
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non PsiMethod in PsiMethod list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
    }
  }

  def checkPsiForObject(element: PsiElement): Boolean = {
    element match {
      case x: ScObject => true
      case _ =>
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non ScObject in ScObject list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
    }
  }
  
  def checkPsiForPackageContainer(element: PsiElement): Boolean = {
    element match {
      case x: ScPackageContainer => true
      case _ =>
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non ScPackageContainer in ScPackageContainer list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        false
    }
  }
  
  def serializeFileStubElement(stub: ScFileStub, dataStream: StubOutputStream) {
    dataStream.writeBoolean(stub.isScript)
    dataStream.writeBoolean(stub.isCompiled)
    dataStream.writeName(stub.packageName)
    dataStream.writeName(stub.getFileName)
  }
  
  def deserializeFileStubElement(dataStream: StubInputStream, parentStub: Object) = {
    val script = dataStream.readBoolean
    val compiled = dataStream.readBoolean
    val packName = dataStream.readName
    val fileName = dataStream.readName
    new ScFileStubImpl(null, packName, fileName, compiled, script)
  }

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil")
}