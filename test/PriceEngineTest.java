import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

public class PriceEngineTest {

    private static Order simpleOrder(double unit, int qty,
                                     boolean expedited, String promo) {
        return new Order(1L,
            List.of(new Order.Line("SKU-1", qty,
                Money.of("USD", unit))),
            expedited, promo);
    }

    private static Customer cust(int loyaltyYears, String region) {
        return new Customer(42L, "Alice", loyaltyYears, region);
    }

    private static double val(Money m) {
        return m.amount().doubleValue();
    }

    @Test
    void basicSubtotalNoDiscountsNoTax() {
        Money q = PriceEngine.quote(
            simpleOrder(100.0, 2, false, null),
            cust(0, "ZZ"));
        assertEquals(200.00, val(q), 0.005);
    }

    @Test
    void expeditedSurchargeAddsFifteen() {
        Money q = PriceEngine.quote(
            simpleOrder(100.0, 1, true, null),
            cust(0, "ZZ"));
        assertEquals(115.00, val(q), 0.005);
    }

    @Test
    void welcome10CodeGivesTenPercentOff() {
        Money q = PriceEngine.quote(
            simpleOrder(100.0, 1, false, "WELCOME10"),
            cust(0, "ZZ"));
        assertEquals(90.00, val(q), 0.005);
    }

    @Test
    void summer15CodeGivesFifteenPercentOff() {
        Money q = PriceEngine.quote(
            simpleOrder(200.0, 1, false, "SUMMER15"),
            cust(0, "ZZ"));
        assertEquals(170.00, val(q), 0.005);
    }

    @Test
    void vip20OnlyAppliesForLoyalCustomers() {
        // Non-loyal: promo ignored. Also no loyalty discount, no tax.
        Money q1 = PriceEngine.quote(
            simpleOrder(100.0, 1, false, "VIP20"),
            cust(0, "ZZ"));
        assertEquals(100.00, val(q1), 0.005);
    }

    @Test
    void loyaltyTierOneGivesTwoPercent() {
        Money q = PriceEngine.quote(
            simpleOrder(100.0, 1, false, null),
            cust(1, "ZZ"));
        // 100 - 2 = 98
        assertEquals(98.00, val(q), 0.005);
    }

    @Test
    void loyaltyTierTwoGivesFivePercent() {
        Money q = PriceEngine.quote(
            simpleOrder(100.0, 1, false, null),
            cust(3, "ZZ"));
        assertEquals(95.00, val(q), 0.005);
    }

    @Test
    void quotesDiscountForLoyalCustomer() {
        // Exactly at the tier-3 boundary: 5 years should get 10% off.
        // This test currently FAILS because of the planted off-by-one
        // in PriceEngine.quote's loyalty branch. That is the bug the
        // student is asked to find in Session 4A.
        Money q = PriceEngine.quote(
            simpleOrder(100.0, 1, false, null),
            cust(5, "ZZ"));
        assertEquals(90.00, val(q), 0.005,
            "5-year loyalty customer should get 10% off (tier 3)");
    }

    @Test
    void euRegionAddsTwentyPercentTax() {
        Money q = PriceEngine.quote(
            simpleOrder(100.0, 1, false, null),
            cust(0, "EU"));
        assertEquals(120.00, val(q), 0.005);
    }

    @Test
    void nullOrEmptyOrderRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> PriceEngine.quote(null, cust(0, "ZZ")));
        Order empty = new Order(1L, List.of(), false, null);
        assertThrows(IllegalArgumentException.class,
            () -> PriceEngine.quote(empty, cust(0, "ZZ")));
        assertThrows(IllegalArgumentException.class,
            () -> PriceEngine.quote(
                simpleOrder(100.0, 1, false, null), null));
    }
}
