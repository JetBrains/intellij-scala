package org.jetbrains.plugins.scala.util.ui;

// See https://discuss.kotlinlang.org/t/scala-does-not-see-kotlin-companion-object-functions/21880/6

import com.intellij.ide.wizard.NewProjectWizardChainStep;
import com.intellij.ide.wizard.NewProjectWizardStep;
import scala.Function1;

class NewProjectWizardChainStepCompanionProxy {
    static <S extends NewProjectWizardStep, NS extends NewProjectWizardStep> NewProjectWizardChainStep<NS> nextStep(
            S step,
            Function1<S, NS> createNext
    ) {
        return NewProjectWizardChainStep.Companion.nextStep(step, createNext::apply);
    }
}
