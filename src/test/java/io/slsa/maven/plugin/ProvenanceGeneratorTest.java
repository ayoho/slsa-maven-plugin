/*
 * Copyright 2023, 2024 International Business Machines Corp..
 * 
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package io.slsa.maven.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Date;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.intoto.attestation.DigestSet;
import io.intoto.attestation.ResourceDescriptor;
import io.intoto.attestation.Statement;
import io.slsa.BuildDefinition;
import io.slsa.BuildMetadata;
import io.slsa.Builder;
import io.slsa.RunDetails;
import io.slsa.SlsaPredicate;
import io.slsa.maven.plugin.exceptions.ProvenanceGenerationException;
import io.slsa.maven.plugin.utils.war.exceptions.MultipleWarsFoundException;
import io.slsa.test.CommonTestUtils;
import io.slsa.test.Constants;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

@ExtendWith(MockitoExtension.class)
public class ProvenanceGeneratorTest {

    @Mock private MavenProject project;
    @Mock private Build projectBuild;
    @Mock private MavenSession mavenSession;
    @Mock private Log log;

    private String builderId = "myBuilderId";
    private String buildType = "myBuildType";
    private CommonTestUtils testUtils = new CommonTestUtils();

    @Test
    void test_generateProvenanceFileData_noWars() {
        ProvenanceGenerator generator = new ProvenanceGenerator(builderId, buildType, project, mavenSession, log);

        when(project.getBuild()).thenReturn(projectBuild);
        when(projectBuild.getDirectory()).thenReturn(Constants.RESOURCES_DIR + File.separator + "no-wars");

        try {
            JsonObject provenanceData = generator.generateProvenanceFileData();
            assertEquals(JsonObject.EMPTY_JSON_OBJECT, provenanceData);
        } catch (ProvenanceGenerationException e) {
            fail("Should not have thrown an exception but did: " + e);
        }
    }

    @Test
    void test_generateProvenanceFileData_multipleWars() {
        ProvenanceGenerator generator = new ProvenanceGenerator(builderId, buildType, project, mavenSession, log);

        when(project.getBuild()).thenReturn(projectBuild);
        when(projectBuild.getDirectory()).thenReturn(Constants.RESOURCES_DIR + File.separator + "multiple-wars");

        try {
            JsonObject provenanceData = generator.generateProvenanceFileData();
            fail("Should have thrown an exception but didn't. Generated provenance data: " + provenanceData);
        } catch (ProvenanceGenerationException e) {
            // Expected
            assertEquals(MultipleWarsFoundException.class.getName(), e.getCause().getClass().getName(), "Exception cause did not match expected value. Full exception was: " + e);
        }
    }

    @Test
    void test_generateProvenanceFileData() {
        ProvenanceGenerator generator = new ProvenanceGenerator(builderId, buildType, project, mavenSession, log);

        when(project.getBuild()).thenReturn(projectBuild);
        when(projectBuild.getDirectory()).thenReturn(Constants.RESOURCES_DIR + File.separator + "one-war");
        when(mavenSession.getStartTime()).thenReturn(new Date());

        try {
            JsonObject statement = generator.generateProvenanceFileData();
            testUtils.assertJsonOnlyContainsKeys("Provenance data", statement, Statement.KEY_TYPE, Statement.KEY_SUBJECT, Statement.KEY_PREDICATE_TYPE, Statement.KEY_PREDICATE);
            verifyStatementType(statement);
            verifyStatementSubject(statement);
            verifyStatementPredicateType(statement);
            verifyStatementPredicate(statement);
        } catch (ProvenanceGenerationException e) {
            fail("Should not have thrown an exception but did: " + e);
        }
    }

    private void verifyStatementType(JsonObject statement) {
        testUtils.assertJsonStringEntryMatches("Statement", statement, Statement.KEY_TYPE, Statement.TYPE_IN_TOTO_STATEMENT);
    }

    private void verifyStatementSubject(JsonObject statement) {
        JsonArray subject = statement.getJsonArray(Statement.KEY_SUBJECT);
        assertEquals(1, subject.size(), "Should have only found 1 entry in the subject, but did not. Full subject was: " + subject);

        JsonObject subjectEntry = subject.getJsonObject(0);
        testUtils.assertJsonOnlyContainsKeys("Subject", subjectEntry, ResourceDescriptor.KEY_NAME, ResourceDescriptor.KEY_DIGEST);

        testUtils.assertJsonStringEntryMatches("Subject", subjectEntry, ResourceDescriptor.KEY_NAME, Constants.FILE_NAME_APP_WAR);

        JsonObject digest = subjectEntry.getJsonObject(ResourceDescriptor.KEY_DIGEST);
        testUtils.assertJsonContainsOnlyExpectedStringEntry("ResourceDescriptor", digest, DigestSet.ALG_SHA256, Constants.SHA_APP_WAR);
    }

    private void verifyStatementPredicateType(JsonObject statement) {
        testUtils.assertJsonStringEntryMatches("Statement", statement, Statement.KEY_PREDICATE_TYPE, SlsaPredicate.PREDICATE_TYPE_SLSA_PROVENANCE_V1);
    }

    private void verifyStatementPredicate(JsonObject statement) {
        JsonObject predicate = statement.getJsonObject(Statement.KEY_PREDICATE);
        testUtils.assertJsonOnlyContainsKeys("Predicate", predicate, SlsaPredicate.KEY_BUILD_DEFINITION, SlsaPredicate.KEY_RUN_DETAILS);
        verifyBuildDefinition(predicate.getJsonObject(SlsaPredicate.KEY_BUILD_DEFINITION));
        verifyRunDetails(predicate.getJsonObject(SlsaPredicate.KEY_RUN_DETAILS));
    }

    private void verifyBuildDefinition(JsonObject buildDefinition) {
        testUtils.assertJsonOnlyContainsKeys("BuildDefinition", buildDefinition, BuildDefinition.KEY_BUILD_TYPE, BuildDefinition.KEY_EXTERNAL_PARAMETERS, BuildDefinition.KEY_RESOLVED_DEPENDENCIES);
        testUtils.assertJsonStringEntryMatches("BuildDefinition", buildDefinition, BuildDefinition.KEY_BUILD_TYPE, buildType);

        JsonObject externalParameters = buildDefinition.getJsonObject(BuildDefinition.KEY_EXTERNAL_PARAMETERS);
        testUtils.assertJsonOnlyContainsKeys("External parameters", externalParameters, ProvenanceGenerator.KEY_EXT_PARAMS_REPOSITORY, ProvenanceGenerator.KEY_EXT_PARAMS_REF);
        testUtils.assertStringMatchesRegex("^git@github.+\\.git$", externalParameters.getString(ProvenanceGenerator.KEY_EXT_PARAMS_REPOSITORY));
        testUtils.assertStringMatchesRegex("^refs/heads/[^/]+$", externalParameters.getString(ProvenanceGenerator.KEY_EXT_PARAMS_REF));
        
        JsonArray resolvedDependencies = buildDefinition.getJsonArray(BuildDefinition.KEY_RESOLVED_DEPENDENCIES);
        assertEquals(1, resolvedDependencies.size(), "Expected to only find one entry in resolved dependencies. Dependencies were: " + resolvedDependencies);
        JsonObject gitRepoDependency = resolvedDependencies.getJsonObject(0);
        testUtils.assertJsonOnlyContainsKeys("Git repo dependency", gitRepoDependency, ResourceDescriptor.KEY_URI, ResourceDescriptor.KEY_DIGEST);
        testUtils.assertStringMatchesRegex("^git\\+https://github.com.+@refs/heads/[^/]+$", gitRepoDependency.getString(ResourceDescriptor.KEY_URI));
        JsonObject digest = gitRepoDependency.getJsonObject(ResourceDescriptor.KEY_DIGEST);
        testUtils.assertStringMatchesRegex("^[a-z0-9]{40}$", digest.getString(DigestSet.GITCOMMIT));
    }

    private void verifyRunDetails(JsonObject runDetails) {
        testUtils.assertJsonOnlyContainsKeys("RunDetails", runDetails, RunDetails.KEY_BUILDER, RunDetails.KEY_METADATA);

        JsonObject builder = runDetails.getJsonObject(RunDetails.KEY_BUILDER);
        testUtils.assertJsonContainsOnlyExpectedStringEntry("Builder", builder, Builder.KEY_ID, builderId);

        JsonObject buildMetadata = runDetails.getJsonObject(RunDetails.KEY_METADATA);
        testUtils.assertJsonOnlyContainsKeys("BuildMetadata", buildMetadata, BuildMetadata.KEY_STARTED_ON);
        // Don't need to verify the exact value of startedOn
    }

}
