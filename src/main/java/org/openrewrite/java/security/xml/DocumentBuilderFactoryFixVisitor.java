/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.security.xml;

import fj.data.Option;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.Dataflow;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.analysis.trait.variable.Variable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;

public class DocumentBuilderFactoryFixVisitor<P> extends XmlFactoryVisitor<P> {

    static final InvocationMatcher DBF_NEW_INSTANCE = InvocationMatcher.fromMethodMatcher(new MethodMatcher("javax.xml.parsers.DocumentBuilderFactory newInstance*()"));
    static final InvocationMatcher DBF_PARSER_SET_FEATURE = InvocationMatcher.fromMethodMatcher(new MethodMatcher("javax.xml.parsers.DocumentBuilderFactory setFeature(java.lang.String, boolean)"));

    private static final String DBF_FQN = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String DISALLOW_DOCTYPE_DECLARATIONS = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String DISABLE_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String DISABLE_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static final List<String> DISALLOWED_DTD_FEATURES = Arrays.asList(
            DISALLOW_DOCTYPE_DECLARATIONS,
            DISABLE_GENERAL_ENTITIES,
            DISABLE_PARAMETER_ENTITIES,
            LOAD_EXTERNAL_DTD
    );

    private static final String SET_X_INCLUDE_AWARE_PROPERTY_NAME = "setXIncludeAware";
    private static final String SET_EXPAND_ENTITY_REFERENCES_PROPERTY_NAME = "setExpandEntityReferences";
    private static final String FEATURE_SECURE_PROCESSING_PROPERTY_NAME = "FEATURE_SECURE_PROCESSING";
    private static final String DBF_INITIALIZATION_METHOD = "dbf-initialization-method";
    private static final String DISALLOWED_DTD_TRUE_MESSAGE = "DTD_DISALLOWED";
    private static final String DISALLOWED_DTD_FALSE_MESSAGE = "DTD_ALLOWED";
    private static final String DBF_VARIABLE_NAME = "dbf-variable-name";

    DocumentBuilderFactoryFixVisitor(ExternalDTDAccumulator acc) {
        super(
                DBF_NEW_INSTANCE,
                DBF_FQN,
                DBF_INITIALIZATION_METHOD,
                DBF_VARIABLE_NAME,
                acc
        );
    }


    private static final class DBFArgumentsSpec extends DataFlowSpec {
        @Override
        public boolean isSource(DataFlowNode srcNode) {
            return findFeatureName(srcNode).isSome();
        }

        @Override
        public boolean isSink(DataFlowNode sinkNode) {
            return DBF_PARSER_SET_FEATURE.advanced().isFirstParameter(sinkNode.getCursor());
        }
    }

//                MethodAccess.Factory.F.firstEnclosingViewOf(sinkNode.getCursor()).toOption()
//                    .filter(ma -> ma.matches(DBF_PARSER_SET_FEATURE))
//                    .filter(ma -> ma.getArguments().get(1) instanceof Literal)
//                    .map(ma -> (Literal) ma.getArguments().get(1))
//                    .bind(Literal::getValue)
//                    .map(Boolean.TRUE::equals)
//                    .isSome();

    private static Option<String> findFeatureName(DataFlowNode node) {
        return node
                // If the DataFlow node is a VarAccess
                .asExpr(VarAccess.class)
                // Get the variable that is being accessed
                .map(VarAccess::getVariable)
                // Get the assigned values to that variable
                .map(Variable::getAssignedValues)
                // If there are assigned values
                .bind(assignedValues -> {
                    if (assignedValues.size() > 1){
                        // If there are more than one assigned values,
                        // we can't determine which one is the one we are looking for
                        return Option.none();
                    }
                    // Iterate even if it's a single value or zero values
                    for (Expr e : assignedValues) {
                        if (e instanceof Literal) {
                            Literal l = (Literal) e;
                            if (DISALLOWED_DTD_FEATURES.contains(l.getValue().orSome("")))
                                return l.getValue().map(String.class::cast);
                        }
                    }
                    return Option.none();
                }).orElse(() -> node
                        // If the DataFlow node is a Literal
                        .asExprParent(Literal.class)
                        // Get the value of the literal
                        .bind(Literal::getValue)
                        // Keep the value only if its one of the DTD Features we're concerned with.
                        .filter(DISALLOWED_DTD_FEATURES::contains)
                        .map(String.class::cast));
    }

    @Override
    public Expression visitExpression(Expression expression, P p) {
        Dataflow.startingAt(getCursor()).findSinks(new DBFArgumentsSpec()).forEach(sink -> {
            Option<String> featureName = DataFlowNode.of(getCursor()).bind(n -> findFeatureName(n));
            sink.getSinkCursors().forEach(sinkCursor -> {
                sinkCursor.putMessageOnFirstEnclosing(J.ClassDeclaration.class, featureName.some(), sinkCursor.dropParentUntil(J.Block.class::isInstance));
            });
        });
        return super.visitExpression(expression, p);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P ctx) {


        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
//        Cursor supportsExternalCursor = getCursor().getMessage(SUPPORTING_EXTERNAL_ENTITIES_PROPERTY_NAME);
//        Cursor supportsFalseDTDCursor = getCursor().getMessage(SUPPORT_DTD_FALSE_PROPERTY_NAME);
//        Cursor supportsDTDTrueCursor = getCursor().getMessage(SUPPORT_DTD_TRUE_PROPERTY_NAME);
        Cursor initializationCursor = getCursor().getMessage(DBF_INITIALIZATION_METHOD);
        XmlFactoryVariable dbfFactoryVariable = getCursor().getMessage(DBF_VARIABLE_NAME);
//        Cursor xmlResolverMethod = getCursor().getMessage(XML_RESOLVER_METHOD);

        Cursor disallowedDTDTrueCursor = getCursor().getMessage(DISALLOW_DOCTYPE_DECLARATIONS);
        Cursor generalEntitiesDisabledCursor = getCursor().getMessage(DISABLE_GENERAL_ENTITIES);
        Cursor parameterEntitiesDisabledCursor = getCursor().getMessage(DISABLE_PARAMETER_ENTITIES);
        Cursor loadExternalDTDCursor = getCursor().getMessage(LOAD_EXTERNAL_DTD);


//        Cursor disallowedDTDFalseCursor = getCursor().getMessage(DISALLOWED_DTD_FALSE_MESSAGE);

        Cursor setPropertyBlockCursor = null;
        if (disallowedDTDTrueCursor == null) {
            setPropertyBlockCursor = initializationCursor;

        } else if (disallowedDTDTrueCursor != null) {
            setPropertyBlockCursor = disallowedDTDTrueCursor;
        }
        if (setPropertyBlockCursor != null && dbfFactoryVariable != null) {
            doAfterVisit(new DBFInsertPropertyStatementVisitor<>(
                    setPropertyBlockCursor.getValue(),
                    dbfFactoryVariable,
                    getAcc().getExternalDTDs().isEmpty(),
                    disallowedDTDTrueCursor == null,
                    generalEntitiesDisabledCursor == null,
                    parameterEntitiesDisabledCursor == null,
                    loadExternalDTDCursor == null
            ));
        }
        return cd;
    }

    public static TreeVisitor<?, ExecutionContext> create(ExternalDTDAccumulator acc) {
        return Preconditions.check(new UsesType<>(DBF_FQN, true), new DocumentBuilderFactoryFixVisitor<ExecutionContext>(acc));
    }
}
