/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.security.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class SensitiveApiEndpoints extends DataTable<SensitiveApiEndpoints.Row> {

    public SensitiveApiEndpoints(Recipe recipe) {
        super(recipe, Row.class, SensitiveApiEndpoints.class.getName(),
                "Sensitive API endpoints",
                "The API endpoints that expose sensitive data.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file containing the API endpoint definition.")
        String sourcePath;

        @Column(displayName = "Method name",
                description = "The name of the method that defines the API endpoint.")
        String methodName;

        @Column(displayName = "Method",
                description = "The HTTP method of the API endpoint.")
        String method;

        @Column(displayName = "Path",
                description = "The path of the API endpoint.")
        String path;

        @Column(displayName = "Sensitive field",
                description = "The piece of sensitive data that is included.")
        String sensitiveField;

        @Column(displayName = "Sensitive data path",
                description = "The sensitive data exposed by the API endpoint.")
        String sensitiveDataPath;
    }
}
