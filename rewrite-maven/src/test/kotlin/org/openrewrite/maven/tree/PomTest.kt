/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.maven.tree

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.maven.internal.RawPom

class PomTest {
    @Test
    fun deserializePom() {
        val pom = """
            <project>
                <modelVersion>4.0.0</modelVersion>
            
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
            
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-dependencies</artifactId>
                            <version>Greenwich.SR6</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            
                <dependencies>
                  <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter-api</artifactId>
                    <version>5.7.0</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
                
                <licenses>
                  <license>
                    <name>Apache License, Version 2.0</name>
                    <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                    <distribution>repo</distribution>
                    <comments>A business-friendly OSS license</comments>
                  </license>
                </licenses>
                
                <repositories>
                  <repository>
                    <releases>
                      <enabled>false</enabled>
                      <updatePolicy>always</updatePolicy>
                      <checksumPolicy>warn</checksumPolicy>
                    </releases>
                    <snapshots>
                      <enabled>true</enabled>
                      <updatePolicy>never</updatePolicy>
                      <checksumPolicy>fail</checksumPolicy>
                    </snapshots>
                    <name>Nexus Snapshots</name>
                    <id>snapshots-repo</id>
                    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
                    <layout>default</layout>
                  </repository>
                </repositories>
            </project>
        """.trimIndent()

        val mapper = XmlMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)

        val model = mapper.readValue(pom, RawPom::class.java)

        assertThat(model.getActiveDependencies(emptyList())[0].groupId)
                .isEqualTo("org.junit.jupiter")

        assertThat(model.dependencyManagement?.dependencies?.dependencies?.first()?.groupId)
                .isEqualTo("org.springframework.cloud")

        assertThat(model.licenses.first()?.name)
                .isEqualTo("Apache License, Version 2.0")

        assertThat(model.getActiveRepositories(emptyList()).first()?.url)
                .isEqualTo("https://oss.sonatype.org/content/repositories/snapshots")
    }
}
