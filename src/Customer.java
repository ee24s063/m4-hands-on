/** A registered customer. loyaltyYears drives the tier discount. */
public record Customer(long id, String name, int loyaltyYears,
                       String region) {}
