# M4 -- Debugging and refactoring with AI

Starter for Sessions 4A (debug) and 4B (refactor).

Run:

    make deps && make test

The `PriceEngine.quote` method is ~90 lines with cyclomatic
complexity roughly 14. It contains ONE planted bug -- an off-by-one
in the loyalty-tier boundary check. Test
`quotesDiscountForLoyalCustomer` fails because of it; every other
test passes.

Session 4A: use your AI chat panel to diagnose and fix the bug.
Session 4B: refactor the loyalty-tier block into a private helper
so the top-level method's complexity drops. PMD/SpotBugs are not
wired in here (they show up in Modules 5/8) -- just javac + JUnit.
