package org.jetbrains.bsp.settings;

/**
 * For more details on why a Java enum is used for {@link org.jetbrains.bsp.settings.BspProjectSettings#preImportConfig}, see
 * the {@link org.jetbrains.bsp.settings.BspProjectSettings.BspServerConfig} Scaladoc.
 */
public enum PreImportConfig {
    NoPreImport,
    AutoPreImport,
    BloopSbtPreImport
}
