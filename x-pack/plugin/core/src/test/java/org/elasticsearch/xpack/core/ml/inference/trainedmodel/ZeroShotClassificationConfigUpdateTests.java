/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.ml.inference.trainedmodel;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xpack.core.ml.inference.InferenceConfigItemTestCase;
import org.elasticsearch.xpack.core.ml.inference.MlInferenceNamedXContentProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xpack.core.ml.inference.trainedmodel.InferenceConfigTestScaffolding.cloneWithNewTruncation;
import static org.elasticsearch.xpack.core.ml.inference.trainedmodel.InferenceConfigTestScaffolding.createTokenizationUpdate;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class ZeroShotClassificationConfigUpdateTests extends InferenceConfigItemTestCase<ZeroShotClassificationConfigUpdate> {

    @Override
    protected boolean supportsUnknownFields() {
        return false;
    }

    @Override
    protected ZeroShotClassificationConfigUpdate doParseInstance(XContentParser parser) throws IOException {
        return ZeroShotClassificationConfigUpdate.fromXContentStrict(parser);
    }

    @Override
    protected Writeable.Reader<ZeroShotClassificationConfigUpdate> instanceReader() {
        return ZeroShotClassificationConfigUpdate::new;
    }

    @Override
    protected ZeroShotClassificationConfigUpdate createTestInstance() {
        return createRandom();
    }

    @Override
    protected ZeroShotClassificationConfigUpdate mutateInstanceForVersion(ZeroShotClassificationConfigUpdate instance, Version version) {
        if (version.before(Version.V_8_1_0)) {
            return new ZeroShotClassificationConfigUpdate(instance.getLabels(), instance.getMultiLabel(), instance.getResultsField(), null);
        }
        return instance;
    }

    public void testFromMap() {
        ZeroShotClassificationConfigUpdate expected = new ZeroShotClassificationConfigUpdate(
            List.of("foo", "bar"),
            false,
            "ml-results",
            new BertTokenizationUpdate(Tokenization.Truncate.FIRST)
        );

        Map<String, Object> config = new HashMap<>() {
            {
                put(ZeroShotClassificationConfig.LABELS.getPreferredName(), List.of("foo", "bar"));
                put(ZeroShotClassificationConfig.MULTI_LABEL.getPreferredName(), false);
                put(ZeroShotClassificationConfig.RESULTS_FIELD.getPreferredName(), "ml-results");
                Map<String, Object> truncate = new HashMap<>();
                truncate.put("truncate", "first");
                Map<String, Object> bert = new HashMap<>();
                bert.put("bert", truncate);
                put("tokenization", bert);
            }
        };
        assertThat(ZeroShotClassificationConfigUpdate.fromMap(config), equalTo(expected));
    }

    public void testFromMapWithUnknownField() {
        ElasticsearchException ex = expectThrows(
            ElasticsearchException.class,
            () -> ZeroShotClassificationConfigUpdate.fromMap(Collections.singletonMap("some_key", 1))
        );
        assertThat(ex.getMessage(), equalTo("Unrecognized fields [some_key]."));
    }

    public void testApply() {
        ZeroShotClassificationConfig originalConfig = new ZeroShotClassificationConfig(
            randomFrom(List.of("entailment", "neutral", "contradiction"), List.of("contradiction", "neutral", "entailment")),
            randomBoolean() ? null : VocabularyConfigTests.createRandom(),
            randomBoolean() ? null : BertTokenizationTests.createRandom(),
            randomAlphaOfLength(10),
            randomBoolean(),
            randomList(1, 5, () -> randomAlphaOfLength(10)),
            randomBoolean() ? null : randomAlphaOfLength(8)
        );

        assertThat(originalConfig, equalTo(new ZeroShotClassificationConfigUpdate.Builder().build().apply(originalConfig)));

        assertThat(
            new ZeroShotClassificationConfig(
                originalConfig.getClassificationLabels(),
                originalConfig.getVocabularyConfig(),
                originalConfig.getTokenization(),
                originalConfig.getHypothesisTemplate(),
                originalConfig.isMultiLabel(),
                List.of("foo", "bar"),
                originalConfig.getResultsField()
            ),
            equalTo(new ZeroShotClassificationConfigUpdate.Builder().setLabels(List.of("foo", "bar")).build().apply(originalConfig))
        );
        assertThat(
            new ZeroShotClassificationConfig(
                originalConfig.getClassificationLabels(),
                originalConfig.getVocabularyConfig(),
                originalConfig.getTokenization(),
                originalConfig.getHypothesisTemplate(),
                true,
                originalConfig.getLabels(),
                originalConfig.getResultsField()
            ),
            equalTo(new ZeroShotClassificationConfigUpdate.Builder().setMultiLabel(true).build().apply(originalConfig))
        );
        assertThat(
            new ZeroShotClassificationConfig(
                originalConfig.getClassificationLabels(),
                originalConfig.getVocabularyConfig(),
                originalConfig.getTokenization(),
                originalConfig.getHypothesisTemplate(),
                originalConfig.isMultiLabel(),
                originalConfig.getLabels(),
                "updated-field"
            ),
            equalTo(new ZeroShotClassificationConfigUpdate.Builder().setResultsField("updated-field").build().apply(originalConfig))
        );

        Tokenization.Truncate truncate = randomFrom(Tokenization.Truncate.values());
        Tokenization tokenization = cloneWithNewTruncation(originalConfig.getTokenization(), truncate);
        assertThat(
            new ZeroShotClassificationConfig(
                originalConfig.getClassificationLabels(),
                originalConfig.getVocabularyConfig(),
                tokenization,
                originalConfig.getHypothesisTemplate(),
                originalConfig.isMultiLabel(),
                originalConfig.getLabels(),
                originalConfig.getResultsField()
            ),
            equalTo(
                new ZeroShotClassificationConfigUpdate.Builder().setTokenizationUpdate(
                    createTokenizationUpdate(originalConfig.getTokenization(), truncate)
                ).build().apply(originalConfig)
            )
        );
    }

    public void testApplyWithEmptyLabelsInConfigAndUpdate() {
        ZeroShotClassificationConfig originalConfig = new ZeroShotClassificationConfig(
            randomFrom(List.of("entailment", "neutral", "contradiction"), List.of("contradiction", "neutral", "entailment")),
            randomBoolean() ? null : VocabularyConfigTests.createRandom(),
            randomBoolean() ? null : BertTokenizationTests.createRandom(),
            randomAlphaOfLength(10),
            randomBoolean(),
            null,
            null
        );

        Exception ex = expectThrows(Exception.class, () -> new ZeroShotClassificationConfigUpdate.Builder().build().apply(originalConfig));
        assertThat(
            ex.getMessage(),
            containsString("stored configuration has no [labels] defined, supplied inference_config update must supply [labels]")
        );
    }

    public void testIsNoop() {
        assertTrue(new ZeroShotClassificationConfigUpdate.Builder().build().isNoop(ZeroShotClassificationConfigTests.createRandom()));
    }

    public static ZeroShotClassificationConfigUpdate createRandom() {
        return new ZeroShotClassificationConfigUpdate(
            randomBoolean() ? null : randomList(1, 5, () -> randomAlphaOfLength(10)),
            randomBoolean() ? null : randomBoolean(),
            randomBoolean() ? null : randomAlphaOfLength(5),
            randomBoolean() ? null : new BertTokenizationUpdate(randomFrom(Tokenization.Truncate.values()))
        );
    }

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        return new NamedXContentRegistry(new MlInferenceNamedXContentProvider().getNamedXContentParsers());
    }

    @Override
    protected NamedWriteableRegistry getNamedWriteableRegistry() {
        return new NamedWriteableRegistry(new MlInferenceNamedXContentProvider().getNamedWriteables());
    }
}
