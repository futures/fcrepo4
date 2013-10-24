
package org.fcrepo.kernel.utils.iterators;

import static com.google.common.collect.Iterators.forArray;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class DifferencingIteratorTest {

    private static final String onlyInCollection = "only in collection";

    private static final String commonValue = "common";

    private static final String onlyInIteratorValue = "only in iterator";

    private DifferencingIterator<String> testIterator;

    private Set<String> toBeCompared = ImmutableSet.of(onlyInCollection,
            commonValue);

    @Test
    public void testDifferencing() {

        final Iterator<String> i =
            forArray(new String[] {onlyInIteratorValue, commonValue});
        testIterator = new DifferencingIterator<String>(toBeCompared, i);

        assertTrue("Didn't see a first value!", testIterator.hasNext());
        assertEquals("Retrieved final results too early!", null, testIterator
                .notCommon());
        assertEquals("Retrieved final results too early!", null, testIterator
                .common());
        assertEquals("Didn't get the only-in-iterator value!",
                onlyInIteratorValue, testIterator.next());
        assertFalse("Shouldn't see any more values!", testIterator.hasNext());
        assertTrue("Didn't find the common value in correct final result!",
                testIterator.common().contains(commonValue));
        assertFalse("Found the common value in wrong final result!",
                testIterator.notCommon().contains(commonValue));
        assertTrue("Didn't find the not-common value in correct final result!",
                testIterator.notCommon().contains(onlyInCollection));
        assertFalse("Found the not-common value in wrong final result!",
                testIterator.common().contains(onlyInCollection));
    }

}
