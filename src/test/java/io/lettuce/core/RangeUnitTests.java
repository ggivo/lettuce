package io.lettuce.core;

import static io.lettuce.TestTags.UNIT_TEST;
import static io.lettuce.core.Range.Boundary.excluding;
import static io.lettuce.core.Range.Boundary.including;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Range}.
 *
 * @author Mark Paluch
 */
@Tag(UNIT_TEST)
class RangeUnitTests {

    @Test
    void unbounded() {

        Range<Object> unbounded = Range.unbounded();

        assertThat(unbounded.getLower().isIncluding()).isTrue();
        assertThat(unbounded.getLower().getValue()).isNull();
        assertThat(unbounded.getLower().isUnbounded()).isTrue();
        assertThat(unbounded.getUpper().isIncluding()).isTrue();
        assertThat(unbounded.getUpper().getValue()).isNull();
        assertThat(unbounded.getUpper().isUnbounded()).isTrue();
        assertThat(unbounded.isUnbounded()).isTrue();
    }

    @Test
    void createIncluded() {

        Range<Object> range = Range.create("ze", "ro");

        assertThat(range.getLower().isIncluding()).isTrue();
        assertThat(range.getLower().getValue()).isEqualTo("ze");
        assertThat(range.getLower().isBounded()).isTrue();
        assertThat(range.getUpper().isIncluding()).isTrue();
        assertThat(range.getUpper().getValue()).isEqualTo("ro");
        assertThat(range.getUpper().isBounded()).isTrue();
        assertThat(range.isUnbounded()).isFalse();
    }

    @Test
    void fromBoundaries() {

        Range<Object> range = Range.from(including("ze"), excluding("ro"));

        assertThat(range.getLower().isIncluding()).isTrue();
        assertThat(range.getLower().getValue()).isEqualTo("ze");
        assertThat(range.getUpper().isIncluding()).isFalse();
        assertThat(range.getUpper().getValue()).isEqualTo("ro");
    }

    @Test
    void greater() {

        Range<Object> gt = Range.unbounded().gt("zero");

        assertThat(gt.getLower().isIncluding()).isFalse();
        assertThat(gt.getLower().getValue()).isEqualTo("zero");
        assertThat(gt.getUpper().isIncluding()).isTrue();
        assertThat(gt.getUpper().getValue()).isNull();
    }

    @Test
    void greaterOrEquals() {

        Range<Object> gte = Range.unbounded().gte("zero");

        assertThat(gte.getLower().isIncluding()).isTrue();
        assertThat(gte.getLower().getValue()).isEqualTo("zero");
        assertThat(gte.getUpper().isIncluding()).isTrue();
        assertThat(gte.getUpper().getValue()).isNull();
    }

    @Test
    void less() {

        Range<Object> lt = Range.unbounded().lt("zero");

        assertThat(lt.getLower().isIncluding()).isTrue();
        assertThat(lt.getLower().getValue()).isNull();
        assertThat(lt.getUpper().isIncluding()).isFalse();
        assertThat(lt.getUpper().getValue()).isEqualTo("zero");
        assertThat(lt.toString()).isEqualTo("Range [[unbounded] to (zero]");
    }

    @Test
    void lessOrEquals() {

        Range<Object> lte = Range.unbounded().lte("zero");

        assertThat(lte.getLower().isIncluding()).isTrue();
        assertThat(lte.getLower().getValue()).isNull();
        assertThat(lte.getUpper().isIncluding()).isTrue();
        assertThat(lte.getUpper().getValue()).isEqualTo("zero");
        assertThat(lte.toString()).isEqualTo("Range [[unbounded] to [zero]");
    }

}
