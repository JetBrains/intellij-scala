package org.jetbrains.plugins.scala.runner;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;

/**
 * @author Alefas
 * @since 02.03.12
 */
public abstract class JavaRuntimeConfigurationProduceBaseAdapter extends JavaRuntimeConfigurationProducerBase {
  protected JavaRuntimeConfigurationProduceBaseAdapter(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  public int compareTo(Object o) {
    return -1;
  }
}
