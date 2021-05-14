package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.openapi.module.Module
import com.intellij.psi.{PsiClass, PsiMethod}
import com.intellij.testIntegration.JavaTestFramework
import org.jetbrains.concurrency.{Promise, Promises}

/**
 * This intermediate class is added to mute/hide some base methods from JavaTestFramework
 * TODO: investigate possibility to extend TestFramework instead of JavaTestFramework cause it has too many redundant methods
 */
abstract class JavaTestFrameworkBridge extends JavaTestFramework {

  /**
   * Needed any non-null value to make "Fix" button in "Create test dialog" work.
   * The hack is required due to how current dialog is implemented in platform.
   *
   * @see [[com.intellij.testIntegration.createTest.CreateTestDialog#onLibrarySelected]]
   */
  override final def getLibraryPath: String =
    this match {
      case _: TestFrameworkSetupSupport => "DUMMY_NON_NULL_VALUE"
      case _                            => null
    }

  override final def setupLibrary(module: Module): Promise[Void] =
    this match {
      case framework: TestFrameworkSetupSupport => framework.setupFramework(module)
      case _                                    => Promises.rejectedPromise()
    }

  //
  // Explicitly override with dummy values: Scala Plugin doesn't use these features,
  //

  // something from automatic generation of test methods, classes, setup, teardown...
  override final def getTestMethodFileTemplateDescriptor: FileTemplateDescriptor = null // TODO: @NotNull in platform
  override final def getTestClassFileTemplateDescriptor: FileTemplateDescriptor = null
  override final def getParametersMethodFileTemplateDescriptor: FileTemplateDescriptor = null
  override final def getAfterClassMethodFileTemplateDescriptor: FileTemplateDescriptor = null
  override final def getBeforeClassMethodFileTemplateDescriptor: FileTemplateDescriptor = null
  override final def getSetUpMethodFileTemplateDescriptor: FileTemplateDescriptor = null
  override final def getTearDownMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  override final def findSetUpMethod(clazz: PsiClass): PsiMethod = null
  override final def findTearDownMethod(clazz: PsiClass): PsiMethod = null
  override final def findOrCreateSetUpMethod(clazz: PsiClass): PsiMethod = null
}
