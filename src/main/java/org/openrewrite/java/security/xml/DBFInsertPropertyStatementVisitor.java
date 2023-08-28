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

import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.Set;

public class DBFInsertPropertyStatementVisitor<P> extends XmlFactoryInsertVisitor<P> {
    private static final Set<String> IMPORTS = Collections.singleton("javax.xml.parsers.ParserConfigurationException");

    private final boolean disallowDoctypes;
    private final boolean disallowGeneralEntities;
    private final boolean disallowParameterEntities;
    private final boolean disallowLoadExternalDTD;

    public DBFInsertPropertyStatementVisitor(
            J.Block scope,
            XmlFactoryVariable dbfFactoryVariable,
            boolean accIsEmpty,
            boolean needsDisallowDoctypesTrue,
            boolean needsDisableGeneralEntities,
            boolean needsDisableParameterEntities,
            boolean needsLoadExternalDTD
    ) {
        super(
                scope,
                dbfFactoryVariable,
                DocumentBuilderFactoryFixVisitor.DBF_NEW_INSTANCE,
                DocumentBuilderFactoryFixVisitor.DBF_PARSER_SET_FEATURE,
                IMPORTS
        );

        if (needsDisallowDoctypesTrue && accIsEmpty) {
            disallowDoctypes = true;
            disallowGeneralEntities = false;
            disallowParameterEntities = false;
            disallowLoadExternalDTD = false;
        } else if (needsDisallowDoctypesTrue && !accIsEmpty) {
            disallowDoctypes = false;
            disallowGeneralEntities = needsDisableGeneralEntities;
            disallowParameterEntities = needsDisableParameterEntities;
            disallowLoadExternalDTD = needsLoadExternalDTD;
        } else if (!needsDisallowDoctypesTrue && !accIsEmpty) {
            disallowDoctypes = false;
            disallowGeneralEntities = false;
            disallowLoadExternalDTD = false;
            disallowParameterEntities = false;
        } else {
            disallowDoctypes = false;
            disallowGeneralEntities = false;
            disallowLoadExternalDTD = false;
            disallowParameterEntities = false;
        }
    }

    @Override
    public void updateTemplate() {
        if (disallowDoctypes && !disallowGeneralEntities && !disallowParameterEntities && !disallowLoadExternalDTD) {
            getTemplate().append(
                    "String FEATURE = \"http://apache.org/xml/features/disallow-doctype-decl\";\n" +
                    "try {\n" +
                    "   " + getFactoryVariableName() + ".setFeature(FEATURE, true);\n" +
                    "} catch (ParserConfigurationException e) {\n" +
                    "    throw new IllegalStateException(\"ParserConfigurationException was thrown. The feature '\"\n" +
                    "            + FEATURE + \"' is not supported by your XML processor.\", e);\n" +
                    "}\n"
            );
        } else if (!disallowDoctypes && disallowGeneralEntities && disallowParameterEntities && disallowLoadExternalDTD) {
            getTemplate().append(
                    "String FEATURE = null;\n" +
                    "try {\n" +
                    "   FEATURE = \"http://xml.org/sax/features/external-parameter-entities\";\n" +
                    "   " + getFactoryVariableName() + ".setFeature(FEATURE, false);\n" +
                    "\n" +
                    "   FEATURE = \"http://apache.org/xml/features/nonvalidating/load-external-dtd\";\n" +
                    "   " + getFactoryVariableName() + ".setFeature(FEATURE, false);\n" +
                    "\n" +
                    "   FEATURE = \"http://xml.org/sax/features/external-general-entities\";\n" +
                    "   " + getFactoryVariableName() + ".setFeature(FEATURE, false);\n" +
                    "\n" +
                    "   " + getFactoryVariableName() + ".setXIncludeAware(false);\n" +
                    "   " + getFactoryVariableName() + ".setExpandEntityReferences(false);\n" +
                    "\n" +
                    "   " + getFactoryVariableName() + ".setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);\n" +
                    "\n" +
                    "} catch (ParserConfigurationException e) {\n" +
                    "    throw new IllegalStateException(\"The feature '\"\n" +
                    "            + FEATURE + \"' is not supported by your XML processor.\", e);\n" +
                    "}\n"
            );
        }
    }
}
