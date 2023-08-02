package org.openrewrite.java.security.xml;

import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

@AllArgsConstructor
public class TransformerFactoryFixVisitor<P> extends JavaIsoVisitor<P> {
    static final MethodMatcher TRANSFORMER_FACTORY_INSTANCE = new MethodMatcher("javax.xml.transform.TransformerFactory new*()");
    static final MethodMatcher TRANSFORMER_FACTORY_SET_ATTRIBUTE = new MethodMatcher("javax.xml.transform.TransformerFactory setAttribute(java.lang.String,java.lang.Object)");

    private static final String TRANSFORMER_FACTORY_FQN = "javax.xml.transform.TransformerFactory";
    private static final String ACCESS_EXTERNAL_DTD_NAME = "ACCESS_EXTERNAL_DTD";
    private static final String ACCESS_EXTERNAL_STYLESHEET_NAME = "ACCESS_EXTERNAL_STYLESHEET";

    private static final String TRANSFORMER_FACTORY_INITIALIZATION_METHOD = "transformer-factory-initialization-method";
    private static final String TRANSFORMER_FACTORY_VARIABLE_NAME = "transformer-factory-variable-name";

    private final ExternalDTDAccumulator acc;

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        Cursor supportsExternalCursor = getCursor().getMessage(ACCESS_EXTERNAL_DTD_NAME);
        Cursor supportsStylesheetCursor = getCursor().getMessage(ACCESS_EXTERNAL_STYLESHEET_NAME);
        Cursor initializationCursor = getCursor().getMessage(TRANSFORMER_FACTORY_INITIALIZATION_METHOD);
        String transformerFactoryVariableName = getCursor().getMessage(TRANSFORMER_FACTORY_VARIABLE_NAME);

        if (initializationCursor != null && transformerFactoryVariableName != null) {
            doAfterVisit(new TransformerFactoryInsertAttributeStatementVisitor<>(
                    initializationCursor.getValue(),
                    transformerFactoryVariableName,
                    supportsExternalCursor == null,
                    supportsStylesheetCursor == null,
                    acc
            ));
        }
        return cd;
    }

    @Override
    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, P ctx) {
        J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);
        if (TypeUtils.isOfClassType(v.getType(), TRANSFORMER_FACTORY_FQN)) {
            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, TRANSFORMER_FACTORY_VARIABLE_NAME, v.getSimpleName());
        }
        return v;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        if (TRANSFORMER_FACTORY_INSTANCE.matches(m)) {
            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, TRANSFORMER_FACTORY_INITIALIZATION_METHOD, getCursor().dropParentUntil(J.Block.class::isInstance));
        } else if (TRANSFORMER_FACTORY_SET_ATTRIBUTE.matches(m) && m.getArguments().get(0) instanceof J.FieldAccess) {
            J.FieldAccess fa = (J.FieldAccess) m.getArguments().get(0);
            if (ACCESS_EXTERNAL_DTD_NAME.equals(fa.getSimpleName())) {
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, ACCESS_EXTERNAL_DTD_NAME, getCursor().dropParentUntil(J.Block.class::isInstance));
            } else if (ACCESS_EXTERNAL_STYLESHEET_NAME.equals(fa.getSimpleName())) {
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, ACCESS_EXTERNAL_STYLESHEET_NAME, getCursor().dropParentUntil(J.Block.class::isInstance));
            }
        }
        return m;
    }

    public static TreeVisitor<?, ExecutionContext> create(ExternalDTDAccumulator acc) {
        return Preconditions.check(new UsesType<>(TRANSFORMER_FACTORY_FQN, true), new TransformerFactoryFixVisitor<>(acc));
    }
}
