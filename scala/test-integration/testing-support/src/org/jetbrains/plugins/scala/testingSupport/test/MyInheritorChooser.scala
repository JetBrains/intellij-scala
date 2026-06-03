package org.jetbrains.plugins.scala.testingSupport
package test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.junit.InheritorChooser
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.components.JBList
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.testingSupport.test.testdata._

import javax.swing.ListCellRenderer
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

private class MyInheritorChooser(
  config: AbstractTestRunConfiguration,
  testData: ClassTestData
) extends InheritorChooser() {

  override def runMethodInAbstractClass(
    context: ConfigurationContext,
    performRunnable: Runnable,
    psiMethod: PsiMethod,
    containingClass: PsiClass,
    acceptAbstractCondition: Condition[_ >: PsiClass]
  ): Boolean = {
    //TODO: SCL-10530 this is mostly copy-paste from InheritorChooser; get rid of this once we support pattern test runs
    if (containingClass == null) return false

    val classes = ClassInheritorsSearch.search(containingClass).asScala
      .filterNot(config.isInvalidSuite)
      .toList
    if (classes.isEmpty) return false

    if (classes.size == 1) {
      runForClass(classes.head, psiMethod, context, performRunnable)
      return true
    }

    val fileEditor = PlatformCoreDataKeys.FILE_EDITOR.getData(context.getDataContext)
    fileEditor match {
      case editor: TextEditor =>
        val document = editor.getEditor.getDocument
        val containingFile = PsiDocumentManager.getInstance(context.getProject).getPsiFile(document)
        containingFile match {
          case owner: PsiClassOwner =>
            val psiClasses = owner.getClasses
            psiClasses.filter(classes.contains(_))
            if (psiClasses.size == 1) {
              runForClass(psiClasses.head, psiMethod, context, performRunnable)
              return true
            }
          case _ =>
        }
      case _ =>
    }

    val renderer  = new PsiClassListCellRenderer()
    val classesSorted = classes.sorted(Ordering.comparatorToOrdering(renderer.getComparator))
    val jbList = new JBList(classesSorted: _*)
    //scala type system gets confused because someone forgot generics in PsiElementListCellRenderer definition
    jbList.setCellRenderer(renderer.asInstanceOf[ListCellRenderer[PsiClass]])
    val testName = if (psiMethod != null) psiMethod.getName else containingClass.getName
    @nowarn("cat=deprecation")
    val pupupFactory = JBPopupFactory.getInstance().createListPopupBuilder(jbList)
    pupupFactory
      .setTitle(TestingSupportBundle.message("test.config.choose.executable.classes.to.run.test", testName))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback(new Runnable() {
        override def run(): Unit = {
          val values = jbList.getSelectedValuesList
          if (values == null) return
          if (values.size == 1) {
            runForClass(values.get(0), psiMethod, context, performRunnable)
          }
        }
      })
    pupupFactory.createPopup().showInBestPositionFor(context.getDataContext)
    true
  }

  override protected def runForClass(
    aClass: PsiClass,
    psiMethod: PsiMethod,
    context: ConfigurationContext,
    performRunnable: Runnable
  ): Unit = {
    testData.setTestClassPath(aClass.qualifiedName)
    config.setName(StringUtil.getShortName(aClass.qualifiedName) + (testData match {
      case single: SingleTestData => "." + single.testName
      case _ => ""
    }))
    Option(ScalaPsiUtil.getModule(aClass)).foreach(config.setModule)
    performRunnable.run()
  }
}
