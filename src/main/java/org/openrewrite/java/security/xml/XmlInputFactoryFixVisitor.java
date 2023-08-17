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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import javax.xml.stream.XMLInputFactory;

public class XmlInputFactoryFixVisitor<P> extends XmlFactoryVisitor<P> {

    static final MethodMatcher XML_PARSER_FACTORY_INSTANCE = new MethodMatcher("javax.xml.stream.XMLInputFactory new*()");
    static final MethodMatcher XML_PARSER_FACTORY_SET_PROPERTY = new MethodMatcher("javax.xml.stream.XMLInputFactory setProperty(java.lang.String, ..)");
    static final MethodMatcher XML_PARSER_FACTORY_SET_RESOLVER = new MethodMatcher("javax.xml.stream.XMLInputFactory setXMLResolver(javax.xml.stream.XMLResolver)");

    private static final String XML_FACTORY_FQN = "javax.xml.stream.XMLInputFactory";
    private static final String SUPPORTING_EXTERNAL_ENTITIES_PROPERTY_NAME = "IS_SUPPORTING_EXTERNAL_ENTITIES";
    private static final String SUPPORT_DTD_FALSE_PROPERTY_NAME = "SUPPORT_DTD";

    private static final String SUPPORT_DTD_TRUE_PROPERTY_NAME = "SUPPORT_DTD_TRUE";
    private static final String XML_PARSER_INITIALIZATION_METHOD = "xml-parser-initialization-method";
    private static final String XML_FACTORY_VARIABLE_NAME = "xml-factory-variable-name";

    private static final String XML_RESOLVER_METHOD = "xml-resolver-initialization-method";

    public XmlInputFactoryFixVisitor(ExternalDTDAccumulator acc) {
        super(
                XML_PARSER_FACTORY_INSTANCE,
                XML_FACTORY_FQN,
                XML_PARSER_INITIALIZATION_METHOD,
                XML_FACTORY_VARIABLE_NAME,
                acc
        );
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        Cursor supportsExternalCursor = getCursor().getMessage(SUPPORTING_EXTERNAL_ENTITIES_PROPERTY_NAME);
        Cursor supportsFalseDTDCursor = getCursor().getMessage(SUPPORT_DTD_FALSE_PROPERTY_NAME);
        Cursor supportsDTDTrueCursor = getCursor().getMessage(SUPPORT_DTD_TRUE_PROPERTY_NAME);
        Cursor initializationCursor = getCursor().getMessage(XML_PARSER_INITIALIZATION_METHOD);
        String xmlFactoryVariableName = getCursor().getMessage(XML_FACTORY_VARIABLE_NAME);
        Cursor xmlResolverMethod = getCursor().getMessage(XML_RESOLVER_METHOD);

        Cursor setPropertyBlockCursor = null;
        if (supportsExternalCursor == null && supportsFalseDTDCursor == null) {
            setPropertyBlockCursor = initializationCursor;
        } else if (supportsExternalCursor == null ^ supportsFalseDTDCursor == null) {
            setPropertyBlockCursor = supportsExternalCursor == null ? supportsFalseDTDCursor : supportsExternalCursor;
        }
        if (setPropertyBlockCursor != null && xmlFactoryVariableName != null) {
            doAfterVisit(new XmlFactoryInsertPropertyStatementVisitor<>(
                    setPropertyBlockCursor.getValue(),
                    xmlFactoryVariableName,
                    supportsExternalCursor == null,
                    supportsFalseDTDCursor == null,
                    getAcc().getExternalDTDs().isEmpty(),
                    supportsDTDTrueCursor == null,
                    xmlResolverMethod == null,
                    getAcc()
            ));
        }
        return cd;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        if (XML_PARSER_FACTORY_SET_PROPERTY.matches(m) && m.getArguments().get(0) instanceof J.FieldAccess) {
            J.FieldAccess fa = (J.FieldAccess) m.getArguments().get(0);
            if (SUPPORTING_EXTERNAL_ENTITIES_PROPERTY_NAME.equals(fa.getSimpleName())) {
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, SUPPORTING_EXTERNAL_ENTITIES_PROPERTY_NAME, getCursor().dropParentUntil(J.Block.class::isInstance));
            } else if (SUPPORT_DTD_FALSE_PROPERTY_NAME.equals(fa.getSimpleName())) {
                checkDTDSupport(m);
            }
        } else if (XML_PARSER_FACTORY_SET_PROPERTY.matches(m) && m.getArguments().get(0) instanceof J.Literal) {
            J.Literal literal = (J.Literal) m.getArguments().get(0);
            if (TypeUtils.isString(literal.getType())) {
                if (XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES.equals(literal.getValue())) {
                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, SUPPORTING_EXTERNAL_ENTITIES_PROPERTY_NAME, getCursor().dropParentUntil(J.Block.class::isInstance));
                } else if (XMLInputFactory.SUPPORT_DTD.equals(literal.getValue())) {
                    checkDTDSupport(m);
                }
            }
        } else if (XML_PARSER_FACTORY_SET_RESOLVER.matches(m)) {
            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, XML_RESOLVER_METHOD, getCursor().dropParentUntil((J.Block.class::isInstance)));
        }
        return m;
    }

    private void checkDTDSupport(J.MethodInvocation m) {
        if (m.getArguments().get(1) instanceof J.Literal) {
            J.Literal literal = (J.Literal) m.getArguments().get(1);
            if (Boolean.TRUE.equals(literal.getValue())) {
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, SUPPORT_DTD_TRUE_PROPERTY_NAME, getCursor().dropParentUntil(J.Block.class::isInstance));
            } else {
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, SUPPORT_DTD_FALSE_PROPERTY_NAME, getCursor().dropParentUntil(J.Block.class::isInstance));
            }
        }
    }

    public static TreeVisitor<?, ExecutionContext> create(ExternalDTDAccumulator acc) {
        return Preconditions.check(new UsesType<>(XML_FACTORY_FQN, true), new XmlInputFactoryFixVisitor<>(acc));
    }
}
