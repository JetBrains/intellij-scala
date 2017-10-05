package org.jetbrains.plugins.scala.runner;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;

/**
 * @author Alefas
 * @since 02.03.12
 */
public abstract class JavaRuntimeConfigurationProduceBaseAdapter<T extends ModuleBasedConfiguration> extends JavaRunConfigurationProducerBase<T> {
  protected JavaRuntimeConfigurationProduceBaseAdapter(ConfigurationType configurationType) {
    super(configurationType);
  }
}
