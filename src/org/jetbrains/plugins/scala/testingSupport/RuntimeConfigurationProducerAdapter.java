package org.jetbrains.plugins.scala.testingSupport;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.RuntimeConfigurationProducer;

/**
 * User: Alexander Podkhalyuzin
 * Date: 12.12.11
 */
abstract public class RuntimeConfigurationProducerAdapter extends RuntimeConfigurationProducer {
  public RuntimeConfigurationProducerAdapter(ConfigurationType configurationType) {
    super(configurationType);
  }

  protected RuntimeConfigurationProducerAdapter(ConfigurationFactory configurationFactory) {
    super(configurationFactory);
  }

  public int compareTo(Object o) {
    return -1;
  }
}
