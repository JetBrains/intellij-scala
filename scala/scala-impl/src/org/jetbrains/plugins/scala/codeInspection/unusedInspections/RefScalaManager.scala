package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.codeInspection.lang.RefManagerExtension
import com.intellij.codeInspection.reference._
import com.intellij.lang.Language
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiJavaModule}
import com.intellij.uast.UastVisitorAdapter
import org.jdom.Element
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.{UClass, UDeclaration, UElement, UastContextKt}

class RefScalaManager extends RefManagerExtension[RefScalaManager] {
  private val Manager: Key[RefScalaManager] = Key.create("RefScalaManager")

  private var myRefManager: RefManager = null
  private var myProjectIterator: PsiElementVisitor = null

  def this(manager: RefManager) = {
    this()
    this.myRefManager = manager
  }

  override def getID: Key[RefScalaManager] = Manager

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def iterate(visitor: RefVisitor): Unit = {
    println(s"iterate visitor: $visitor")
  }

  override def cleanup(): Unit = {}

  override def removeReference(refElement: RefElement): Unit = {
    println(s"removeReference: $refElement")
  }

  override def createRefElement(psi: PsiElement): RefElement = {
    println(s"createRefElement: $psi")
        val uElement = if (psi.isInstanceOf[UElement]) psi.asInstanceOf[UElement]
        else UastContextKt.toUElement(psi)

    if (uElement.isInstanceOf[UClass]) {
      println(s"UClass detected for $psi")
      return new ScalaRefClass(uElement.asInstanceOf[UClass], psi, myRefManager)
    }

    null
    //    if (!getLanguages.contains(psi.getLanguage)) return null
    //    if (psi.isInstanceOf[PsiFile]) return new RefJavaFileImpl(psi.asInstanceOf[PsiFile], myRefManager)
    //    else if (psi.isInstanceOf[PsiJavaModule]) return new RefJavaModuleImpl(psi.asInstanceOf[PsiJavaModule], myRefManager)
    //    val uElement = if (psi.isInstanceOf[UElement]) psi.asInstanceOf[UElement]
    //    else UastContextKt.toUElement(psi)
    //    if (uElement.isInstanceOf[UClass]) return new RefClassImpl(uElement.asInstanceOf[UClass], psi, myRefManager)
    //    if (uElement.isInstanceOf[UMethod]) return new RefMethodImpl(uElement.asInstanceOf[UMethod], psi, myRefManager)
    //    else if (uElement.isInstanceOf[UField]) return new RefFieldImpl(uElement.asInstanceOf[UField], psi, myRefManager)
    //    else if (uElement.isInstanceOf[ULambdaExpression] || uElement.isInstanceOf[UCallableReferenceExpression]) return new RefFunctionalExpressionImpl(uElement.asInstanceOf[UExpression], psi, myRefManager)
    //    return null
  }

  override def getReference(`type`: String, fqName: String): RefEntity = {
    null
    //    if (IMPLICIT_CONSTRUCTOR == `type`) return getImplicitConstructor(fqName)
    //    if (METHOD == `type`) return RefMethodImpl.methodFromExternalName(myRefManager, fqName)
    //    if (CLASS == `type`) return RefClassImpl.classFromExternalName(myRefManager, fqName)
    //    if (FIELD == `type`) return RefFieldImpl.fieldFromExternalName(myRefManager, fqName)
    //    if (PARAMETER == `type`) return RefParameterImpl.parameterFromExternalName(myRefManager, fqName)
    //    if (PACKAGE == `type`) return RefPackageImpl.packageFromFQName(myRefManager, fqName)
    //    if (JAVA_MODULE == `type`) return RefJavaModuleImpl.moduleFromExternalName(myRefManager, fqName)
    //    return null
  }

  override def getType(entity: RefEntity): String = "getType placeholder"

  override def getRefinedElement(ref: RefEntity): RefEntity =
    null

  override def visitElement(element: PsiElement): Unit = {
    if (myProjectIterator == null) myProjectIterator = new UastVisitorAdapter(new MyScalaElementVisitor(), true) {
      override def visitElement(element: PsiElement): Unit = {
        super.visitElement(element)
      }
    }
    element.accept(myProjectIterator)
  }

  override def getGroupName(entity: RefEntity): String = {
    "getGroupName placeholder"
  }

  override def belongsToScope(psiElement: PsiElement): Boolean = {
    if (psiElement.isInstanceOf[ScClass] && psiElement.toString.contains("UsedClass1")) {
      println(s"belongsToScope returning true for: $psiElement")
      true
    } else {
      false
    }
  }

  override def `export`(refEntity: RefEntity, element: Element): Unit = {}

  override def onEntityInitialized(refEntity: RefElement, psiElement: PsiElement): Unit = {}

  private class MyScalaElementVisitor extends AbstractUastNonRecursiveVisitor {
    override def visitDeclaration(node: UDeclaration): Boolean = {
      val refElement = myRefManager.getReference(node.getSourcePsi)
      if (refElement != null) myRefManager.asInstanceOf[RefManagerImpl].buildReferences(refElement)
      true
    }
  }
}
