/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.runtimefields.query;

import org.apache.lucene.util.BytesRef;

import static org.hamcrest.Matchers.equalTo;

public class IpScriptFieldExistsQueryTests extends AbstractIpScriptFieldQueryTestCase<IpScriptFieldExistsQuery> {
    @Override
    protected IpScriptFieldExistsQuery createTestInstance() {
        return new IpScriptFieldExistsQuery(randomScript(), leafFactory, randomAlphaOfLength(5));
    }

    @Override
    protected IpScriptFieldExistsQuery copy(IpScriptFieldExistsQuery orig) {
        return new IpScriptFieldExistsQuery(orig.script(), leafFactory, orig.fieldName());
    }

    @Override
    protected IpScriptFieldExistsQuery mutate(IpScriptFieldExistsQuery orig) {
        if (randomBoolean()) {
            new IpScriptFieldExistsQuery(randomValueOtherThan(orig.script(), this::randomScript), leafFactory, orig.fieldName());
        }
        return new IpScriptFieldExistsQuery(orig.script(), leafFactory, orig.fieldName() + "modified");
    }

    @Override
    public void testMatches() {
        assertTrue(createTestInstance().matches(new BytesRef[0], randomIntBetween(1, Integer.MAX_VALUE)));
        assertFalse(createTestInstance().matches(new BytesRef[0], 0));
        assertFalse(createTestInstance().matches(new BytesRef[] { new BytesRef("not even an IP") }, 0));
    }

    @Override
    protected void assertToString(IpScriptFieldExistsQuery query) {
        assertThat(query.toString(query.fieldName()), equalTo("IpScriptFieldExistsQuery"));
    }
}
