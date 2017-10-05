package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.ResolveResult;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DynamicTypeReferenceResolver {
    public static ExtensionPointName<DynamicTypeReferenceResolver> EP_NAME =
            ExtensionPointName.create("org.intellij.scala.scalaDynamicTypeResolver");

    public abstract ResolveResult[] resolve(ScReferenceExpression expression);

    public static List<ResolveResult> getAllResolveResult(ScReferenceExpression expression) {
        List<ResolveResult> all = new ArrayList<ResolveResult>();

        for (DynamicTypeReferenceResolver resolver : EP_NAME.getExtensions()) {
            ResolveResult[] result = resolver.resolve(expression);
            if (result != null && result.length != 0) {
                all.addAll(Arrays.asList(result));
            }
        }
        return all;
    }
}
