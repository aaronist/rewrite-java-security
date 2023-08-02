package org.openrewrite.java.security.xml;

import org.openrewrite.Cursor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

public class TransformerFactoryInsertAttributeStatementVisitor<P> extends JavaIsoVisitor<P> {
    private final J.Block scope;
    private final StringBuilder propertyTemplate = new StringBuilder();
    private final ExternalDTDAccumulator acc;
    private final String transformerFactoryVariableName;

    public TransformerFactoryInsertAttributeStatementVisitor(
            J.Block scope,
            String factoryVariableName,
            boolean needsExternalEntitiesDisabled,
            boolean needsStylesheetsDisabled,
            ExternalDTDAccumulator acc
    ) {
        this.scope = scope;
        this.transformerFactoryVariableName = factoryVariableName;
        this.acc = acc;

        if (needsExternalEntitiesDisabled) {
            propertyTemplate.append(transformerFactoryVariableName).append(".setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, \"\");");
        }
        if (needsStylesheetsDisabled) {
            propertyTemplate.append(transformerFactoryVariableName).append(".setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, \"\");");
        }
    }

    @Override
    public J.Block visitBlock(J.Block block, P ctx) {
        J.Block b = super.visitBlock(block, ctx);
        Statement beforeStatement = null;
        if (b.isScope(scope)) {
            for (int i = b.getStatements().size() - 2; i > -1; i--) {
                Statement st = b.getStatements().get(i);
                Statement stBefore = b.getStatements().get(i + 1);
                if (st instanceof J.MethodInvocation) {
                    J.MethodInvocation m = (J.MethodInvocation) st;
                    if (TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_INSTANCE.matches(m) || TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_SET_ATTRIBUTE.matches(m)) {
                        beforeStatement = stBefore;
                    }
                } else if (st instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) st;
                    if (vd.getVariables().get(0).getInitializer() instanceof J.MethodInvocation) {
                        J.MethodInvocation m = (J.MethodInvocation) vd.getVariables().get(0).getInitializer();
                        if (m != null && TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_INSTANCE.matches(m)) {
                            beforeStatement = stBefore;
                        }
                    }
                }
            }

            if (getCursor().getParent() != null && getCursor().getParent().getValue() instanceof J.ClassDeclaration) {
                propertyTemplate.insert(0, "{\n").append("}");
            }
            JavaCoordinates insertCoordinates = beforeStatement != null ?
                    beforeStatement.getCoordinates().before() :
                    b.getCoordinates().lastStatement();
            b = JavaTemplate
                    .builder(propertyTemplate.toString())
                    .contextSensitive()
                    .build()
                    .apply(new Cursor(getCursor().getParent(), b), insertCoordinates);
        }
        return b;
    }
}
