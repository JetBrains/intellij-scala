package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.Arrays;

public class ZincProjectSettingsImpl extends JpsElementBase<ZincProjectSettingsImpl> implements ZincProjectSettings {
    public static ZincProjectSettings defaults() {
        return new ZincProjectSettingsImpl(false, false, new String[0]);
    }

    private boolean myCompileToJar;
    private boolean myIgnoreScalacOptions;
    private String[] myIgnoredScalacOptions;

    public ZincProjectSettingsImpl(boolean compileToJar, boolean ignoreScalacOptions, String[] ignoredScalacOptions) {
        this.myCompileToJar = compileToJar;
        this.myIgnoreScalacOptions = ignoreScalacOptions;
        this.myIgnoredScalacOptions = ignoredScalacOptions;
    }

    @Override
    public boolean isCompileToJar() {
        return myCompileToJar;
    }

    @Override
    public boolean isIgnoringScalacOptions() {
        return myIgnoreScalacOptions;
    }

    @Override
    public String[] getIgnoredScalacOptions() {
        return myIgnoredScalacOptions;
    }

    @NotNull
    @Override
    public ZincProjectSettingsImpl createCopy() {
        String[] ignoredScalacOptionsCopy = Arrays.copyOf(myIgnoredScalacOptions, myIgnoredScalacOptions.length);
        return new ZincProjectSettingsImpl(myCompileToJar, myIgnoreScalacOptions, ignoredScalacOptionsCopy);
    }

    @Override
    public void applyChanges(@NotNull ZincProjectSettingsImpl modified) {

    }
}
