package nz.co.ksktech.cashbook.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalisesToScaleTwoHalfUp() {
        assertThat(Money.of("1.005")).hasToString("1.01");
        assertThat(Money.of("-1.005")).hasToString("-1.01");
        assertThat(Money.of("7")).hasToString("7.00");
    }

    @Test
    void convertsCents() {
        assertThat(Money.ofCents(-5498)).isEqualTo(new BigDecimal("-54.98"));
        assertThat(Money.toCents(Money.of("2899.00"))).isEqualTo(289900L);
    }

    @Test
    void boxIntersection() {
        BoundingBox line = new BoundingBox(0, 0, 100, 10);
        assertThat(line.intersectionArea(new BoundingBox(90, 5, 20, 20))).isEqualTo(50f);
        assertThat(line.intersectionArea(new BoundingBox(0, 20, 10, 10))).isZero();
    }
}
