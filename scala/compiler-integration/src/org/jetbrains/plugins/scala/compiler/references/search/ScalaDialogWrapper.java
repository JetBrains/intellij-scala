package org.jetbrains.plugins.scala.compiler.references.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

/**
 * A base class for {@link DialogWrapper} implementations written in Scala 3.
 *
 * <p>{@link DialogWrapper.DialogWrapperAction} is a {@code protected} inner class. Scala 3 compiles the
 * body of an anonymous (or nested) subclass into a separate class file, and then refuses to emit the
 * reference to the protected constructor:
 *
 * <pre>Unable to emit reference to constructor DialogWrapperAction in class DialogWrapperAction,
 * class DialogWrapperAction is not accessible in anonymous class DialogWrapper.this.DialogWrapperAction {...}</pre>
 *
 * <p>This is a compiler bug rather than a genuine accessibility violation: the emitted class does derive
 * from {@code DialogWrapperAction}, so the JVM accepts the constructor call, and the equivalent Scala 2
 * and Java code both compile and run. See
 * <a href="https://github.com/scala/scala3/issues/24507">scala/scala3#24507</a>, fixed in Scala 3.8.2.
 * Once the plugin is built with 3.8.2 or newer, this class can be deleted and the dialogs can extend
 * {@code DialogWrapperAction} anonymously again.
 *
 * <p>Written in Java on purpose: {@code javac} has no such restriction, and going through
 * {@link #dialogAction(String, Consumer)} keeps the exact semantics of {@code DialogWrapperAction} --
 * including the {@code myClosed} check and the {@code SlowOperations} section, neither of which can be
 * reproduced from plugin code.
 */
// The split-mode inspection flags DialogWrapper in a 'shared' module, but this class exists precisely
// to subclass DialogWrapper; the Scala dialogs it serves have always extended it directly.
    
@SuppressWarnings("SplitModeApiUsage")
public abstract class ScalaDialogWrapper extends DialogWrapper {

    protected ScalaDialogWrapper(boolean canBeParent) {
        super(canBeParent);
    }

    protected ScalaDialogWrapper(@Nullable Project project, boolean canBeParent) {
        super(project, canBeParent);
    }

    protected ScalaDialogWrapper(@Nullable Project project, boolean canBeParent, @NotNull IdeModalityType ideModalityType) {
        super(project, canBeParent, ideModalityType);
    }

    /**
     * Creates a {@link DialogWrapper.DialogWrapperAction} which runs {@code action} when triggered.
     *
     * @param name   the button text (see {@link Action#NAME})
     * @param action the work to perform, see {@link DialogWrapper.DialogWrapperAction#doAction(ActionEvent)}
     */
    protected final @NotNull Action dialogAction(@NlsContexts.Button @NotNull String name,
                                                 @NotNull Consumer<ActionEvent> action) {
        return new DialogWrapperAction(name) {
            @Override
            protected void doAction(ActionEvent e) {
                action.accept(e);
            }
        };
    }
}
