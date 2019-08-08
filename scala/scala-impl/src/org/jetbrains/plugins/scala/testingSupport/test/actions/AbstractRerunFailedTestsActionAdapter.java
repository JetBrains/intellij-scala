package org.jetbrains.plugins.scala.testingSupport.test.actions;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.openapi.ui.ComponentContainer;
import scala.Tuple2;
import scala.collection.Seq;

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.12.11
 */

public abstract class AbstractRerunFailedTestsActionAdapter extends AbstractRerunFailedTestsAction {
  protected AbstractRerunFailedTestsActionAdapter(ComponentContainer componentContainer) {
    super(componentContainer);
  }

  public abstract class MyRunProfileAdapter extends AbstractRerunFailedTestsAction.MyRunProfile {
    public MyRunProfileAdapter(RunConfigurationBase configuration) {
      super(configuration);
    }


    protected Seq<Tuple2<String, String>> previoslyFailed = null;
  }
}
