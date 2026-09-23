import java.math.BigDecimal;
import java.math.RoundingMode;

/** Minimal money value type (currency + amount, 2-decimal rounding). */
public record Money(String currency, BigDecimal amount) {

    public static Money of(String currency, double amount) {
        return new Money(currency,
            BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP));
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(currency,
            amount.add(other.amount).setScale(2, RoundingMode.HALF_UP));
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(currency,
            amount.subtract(other.amount).setScale(2, RoundingMode.HALF_UP));
    }

    public Money times(double factor) {
        return new Money(currency,
            amount.multiply(BigDecimal.valueOf(factor))
                  .setScale(2, RoundingMode.HALF_UP));
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
