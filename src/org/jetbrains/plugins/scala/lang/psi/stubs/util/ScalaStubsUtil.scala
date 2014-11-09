package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package util


import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.stubs.{StubIndex, StubInputStream, StubOutputStream}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFileStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ScDirectInheritorsIndex, ScSelfTypeInheritorsIndex}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaStubsUtil {
  def getClassInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null) return Seq.empty
    val inheritors = new ArrayBuffer[ScTemplateDefinition]
    val iterator: java.util.Iterator[ScExtendsBlock] =
      StubIndex.getElements(ScDirectInheritorsIndex.KEY, name, clazz.getProject, scope, classOf[ScExtendsBlock]).iterator
    while (iterator.hasNext) {
      val extendsBlock: PsiElement = iterator.next
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
    inheritors.toSeq
  }

  def getSelfTypeInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null) return Seq.empty
    val inheritors = new ArrayBuffer[ScTemplateDefinition]
    def processClass(inheritedClazz: PsiClass) {
      inReadAction {
        val iterator: java.util.Iterator[ScSelfTypeElement] =
          StubIndex.getElements(ScSelfTypeInheritorsIndex.KEY, name, inheritedClazz.getProject, scope, classOf[ScSelfTypeElement]).iterator
        while (iterator.hasNext) {
          val selfTypeElement = iterator.next
          selfTypeElement.typeElement match {
            case Some(typeElement) =>
              typeElement.getType(TypingContext.empty) match {
                case Success(tp, _) =>
                  def checkTp(tp: ScType): Boolean = {
                    tp match {
                      case c: ScCompoundType =>
                        c.components.exists(checkTp)
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