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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.security.XmlParserXXEVulnerability;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

public class DocumentBuilderFactoryXXETest implements RewriteTest{

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new XmlParserXXEVulnerability());
    }

    @Test
    void factoryIsNotVulnerable() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.ParserConfigurationException;
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                
                void testSetFeature(){
                    String FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
                    try {
                        dbf.setFeature(FEATURE, true);
                    } catch (ParserConfigurationException e) {
                        throw new IllegalStateException("ParserConfigurationException was thrown. The feature '"
                                  + FEATURE + "' is not supported by your XML processor.", e);
                    }
                }
                DocumentBuilder safebuilder = dbf.newDocumentBuilder();
              }
              """
          )
        );
    }

    @Test
    void factoryIsNotVulnerableLiteralAccess() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.ParserConfigurationException; // catching unsupported features
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                
                void testSetFeature(){
//                    String FEATURE = null;
                    try {
                        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    } catch (ParserConfigurationException e) {
                        logger.info("ParserConfigurationException was thrown. The feature is not supported by your XML processor.");
                    }
                }
                DocumentBuilder safebuilder = dbf.newDocumentBuilder();
              }
              """
          )
        );
    }

    @Test
    void factoryIsVulnerable() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.DocumentBuilder;
              import javax.xml.parsers.ParserConfigurationException; // catching unsupported features
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                  DocumentBuilder safebuilder = dbf.newDocumentBuilder();
              }
              """,
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.DocumentBuilder;
              import javax.xml.parsers.ParserConfigurationException; // catching unsupported features
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                  
                  {
                      String FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
                      try {
                          dbf.setFeature(FEATURE, true);
                      } catch (ParserConfigurationException e) {
                          throw new IllegalStateException("ParserConfigurationException was thrown. The feature '"
                                  + FEATURE + "' is not supported by your XML processor.", e);
                      }

                  }
                  DocumentBuilder safebuilder = dbf.newDocumentBuilder();
              }
              """
          )
        );
    }

    @Test
    void factoryIsNotVulnerableStringLiteralAssignedToStaticField() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.ParserConfigurationException; // catching unsupported features
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                  private static final String feature = "http://apache.org/xml/features/disallow-doctype-decl";
                  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                  {
                      try {
                          dbf.setFeature(feature, true);
                      } catch (ParserConfigurationException e) {
                          throw new IllegalStateException("ParserConfigurationException was thrown. The feature is not supported by your XML processor.", e);
                      }
                  }
                  DocumentBuilder safebuilder = dbf.newDocumentBuilder();
              }
              """
          )
        );
    }

    @Test
    void factoryIsSafeButNeedsDTDs() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.ParserConfigurationException; // catching unsupported features
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                  {
                      String FEATURE = null;
                      try {
                          FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
                          dbf.setFeature(FEATURE, true);
                          
                          FEATURE = "http://xml.org/sax/features/external-parameter-entities";
                          dbf.setFeature(FEATURE, false);
                      
                          // Disable external DTDs as well
                          FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
                          dbf.setFeature(FEATURE, false);
                          
                          FEATURE = "http://xml.org/sax/features/external-general-entities";
                          dbf.setFeature(FEATURE, false);
                          
                          // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
                          dbf.setXIncludeAware(false);
                          dbf.setExpandEntityReferences(false);
                      
                          // As stated in the documentation "Feature for Secure Processing (FSP)" is the central mechanism to\s
                          // help safeguard XML processing. It instructs XML processors, such as parsers, validators,\s
                          // and transformers, to try and process XML securely. This can be used as an alternative to
                          // dbf.setExpandEntityReferences(false); to allow some safe level of Entity Expansion
                          // Exists from JDK6.
                          dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                          
                      } catch (ParserConfigurationException e) {
                          throw new IllegalStateException("ParserConfigurationException was thrown. The feature '"
                                  + FEATURE + "' is not supported by your XML processor.", e);
                      }
                  }
                  DocumentBuilder safebuilder = dbf.newDocumentBuilder();
              }
              """
          )
        );
    }

    @Test
//    @Disabled("Temporarily")
    void factoryIsVulnerableWithPublicAndSystemIdPresent() {
        rewriteRun(
          xml(
            """
              <!DOCTYPE xml [
                  <!ENTITY open-hatch-system
                    SYSTEM "http://www.textuality.com/boilerplate/OpenHatch.xml">
                  <!ENTITY open-hatch-public
                    PUBLIC "-//Textuality//TEXT Standard open-hatch boilerplate//EN"
                    "http://www.texty.com/boilerplate/OpenHatch.xml">
                  <!ENTITY hatch-pic
                    SYSTEM "../grafix/OpenHatch.gif"
                    NDATA gif>
              ]>
              <root>
                <!-- Your XML content here -->
              </root>
              """
          ),
          java(
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.DocumentBuilder;
              import javax.xml.parsers.ParserConfigurationException; // catching unsupported features
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                  DocumentBuilder safebuilder = dbf.newDocumentBuilder();
              }
              """,
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.DocumentBuilder;
              import javax.xml.parsers.ParserConfigurationException; // catching unsupported features
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                  
                  {
                      String FEATURE = null;
                      try {
                          FEATURE = "http://xml.org/sax/features/external-parameter-entities";
                          dbf.setFeature(FEATURE, false);
                      
                          FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
                          dbf.setFeature(FEATURE, false);
                          
                          FEATURE = "http://xml.org/sax/features/external-general-entities";
                          dbf.setFeature(FEATURE, false);
                          
                          dbf.setXIncludeAware(false);
                          dbf.setExpandEntityReferences(false);
                          
                          dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                          
                      } catch (ParserConfigurationException e) {
                          throw new IllegalStateException("The feature '"
                                  + FEATURE + "' is not supported by your XML processor.", e);
                      }

                  }
                  DocumentBuilder safebuilder = dbf.newDocumentBuilder();
              }
              """
          )
        );

    }

}
