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

import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

class XmlFactoryInsertPropertyStatementVisitor<P> extends XmlFactoryInsertVisitor<P> {
    private static final Set<String> IMPORTS = new HashSet<>(Arrays.asList(
            "java.util.Collection",
            "javax.xml.stream.XMLStreamException",
            "java.util.Arrays",
            "java.util.Collections"
            ));

    private final ExternalDTDAccumulator acc;

    private final boolean needsExternalEntitiesDisabled;
    private final boolean needsSupportDTDFalse;
    private final boolean accIsEmpty;
    private final boolean needsSupportDTDTrue;
    private final boolean needsResolverMethod;
    private final boolean generateAllowList;

    public XmlFactoryInsertPropertyStatementVisitor(
            J.Block scope,
            String factoryVariableName,
            boolean needsExternalEntitiesDisabled,
            boolean needsSupportDTDFalse,
            boolean accIsEmpty,
            boolean needsSupportDTDTrue,
            boolean needsResolverMethod,
            ExternalDTDAccumulator acc
    ) {
        super(
                scope,
                factoryVariableName,
                XmlInputFactoryFixVisitor.XML_PARSER_FACTORY_INSTANCE,
                XmlInputFactoryFixVisitor.XML_PARSER_FACTORY_SET_PROPERTY,
                IMPORTS
        );

        this.acc = acc;
        this.needsExternalEntitiesDisabled = needsExternalEntitiesDisabled;
        this.needsSupportDTDFalse = needsSupportDTDFalse;
        this.accIsEmpty = accIsEmpty;
        this.needsSupportDTDTrue = needsSupportDTDTrue;
        this.needsResolverMethod = needsResolverMethod;
        this.generateAllowList = (!needsSupportDTDTrue || needsSupportDTDFalse) && !accIsEmpty && needsResolverMethod;
    }

    @Override
    public void updateTemplate() {
        if (needsExternalEntitiesDisabled) {
            getTemplate().append(getFactoryVariableName()).append(".setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);");
        }
        if (needsSupportDTDFalse && needsSupportDTDTrue) {
            if (accIsEmpty) {
                getTemplate().append(getFactoryVariableName()).append(".setProperty(XMLInputFactory.SUPPORT_DTD, false);");
            } else if (needsResolverMethod) {
                getTemplate().append(getFactoryVariableName()).append(".setProperty(XMLInputFactory.SUPPORT_DTD, true);");
            }
        }

        if (!accIsEmpty && generateAllowList) {
            String newAllowListVariableName = VariableNameUtils.generateVariableName(
                    "allowList",
                    getCursor(),
                    VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER
            );

            if (acc.getExternalDTDs().size() > 1) {
                getTemplate().append(
                        "Collection<String>" + newAllowListVariableName + " = Arrays.asList(\n"
                );
            } else {
                getTemplate().append(
                        "Collection<String>" + newAllowListVariableName + " = Collections.singleton(\n"
                );
            }

            String allowListContent = acc.getExternalDTDs().stream().map(dtd -> '"' + dtd + '"').collect(Collectors.joining(
                    ",\n\t",
                    "\t",
                    ""
            ));
            getTemplate().append(allowListContent).append("\n);\n");
            getTemplate().append(getFactoryVariableName()).append(
                    ".setXMLResolver((publicID, systemID, baseURI, namespace) -> {\n" +
                    "   if (" + newAllowListVariableName + ".contains(systemID)){\n" +
                    "       // returning null will cause the parser to resolve the entity\n" +
                    "       return null;\n" +
                    "   }\n" +
                    "   throw new XMLStreamException(\"Loading of DTD was blocked to prevent XXE: \" + systemID);\n" +
                    "});"
            );
        }
    }
}
