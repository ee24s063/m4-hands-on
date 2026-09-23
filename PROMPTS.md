# PROMPTS.md - Module 4 Hands-on

# Session 4A: AI-assisted debugging

`make test` gave 1 failure out of 10 - `quotesDiscountForLoyalCustomer`.

## Part A - Explain the stack trace

Prompt used every time (didn't change the wording):

```
Explain this Java stack trace in plain English. What is
the most likely root cause? Do NOT propose a patch yet.
```

First line of the trace:

```
org.opentest4j.AssertionFailedError: 5-year loyalty customer should get 10% off (tier 3)
  ==> expected: <90.0> but was: <95.0>
```

How it went as I added more lines:

- **1 line (just the assertion message):** only a generic answer, basically "some assertion failed, the value is 5 off from what was expected". It guessed something was wrong with the discount but couldn't say where.
- **Adding lines down to the test frame (`PriceEngineTest.java:92`):** this is where it got it right. Once it had the test line it looked at the test and then at `PriceEngine.java`, and found the loyalty check.
- **Full trace:** same answer as above, nothing new. The extra frames are all JUnit / reflection stuff.

One thing I noticed: Claude Code had the project folder open, so it was reading `PriceEngine.java` itself and not just going off the trace. That's why it could quote line 57 directly.

AI's explanation (verbatim, the reply where it got the right answer):

> This isn't a crash — it's JUnit's assertion machinery (AssertEquals.assertEquals → assertNotEqual → AssertionFailureBuilder) reporting that a value didn't match. The rest of the frames (AssertionFailureBuilder.build/buildAndThrow, AssertEquals.failNotEqual) are just JUnit's internal plumbing for constructing and throwing that failure once it detected 90.0 != 95.0. Method.invoke and the two ArrayList.forEach frames are the JUnit Platform's test-runner machinery reflectively invoking your @Test method — not application logic, just how JUnit discovers and calls test methods.
>
> The one frame that actually matters is: `PriceEngineTest.quotesDiscountForLoyalCustomer(PriceEngineTest.java:92)`
> That's the line in the test (the assertEquals call) where the comparison failed. Everything above it in the trace is just JUnit unwinding back up to report that failure — there's no exception thrown by your production code.
>
> **Most likely root cause**
>
> Look at PriceEngine.java:57:
>
> ```java
> if (years > 5) {
>     loyaltyRate = 0.10;
> } else if (years >= 3) {
>     loyaltyRate = 0.05;
> ```
>
> The comment right above it says tier 3 is "5+ years": 10% off. But the code tests years > 5, not years >= 5. For a customer with exactly loyaltyYears = 5, that condition is false, so control falls through to years >= 3, which applies the tier-2 rate (5%) instead of tier-3 (10%). That's why the quote comes back as 95.0 instead of 90.0 — a classic off-by-one (strict > where the intended boundary is >=) at the tier-3 threshold. The test file's own comment (PriceEngineTest.java:86-88) confirms this is a deliberately planted off-by-one in that exact branch.

My note: I agree, 5 years is supposed to be tier 3 but `> 5` puts it in tier 2, and 95 vs 90 is exactly the 5% vs 10% gap. I didn't need the full trace here, the message plus the test file frame was enough.

## Part B - Propose and verify the patch

Prompt:

```
Given root cause X (the tier-3 check in PriceEngine.java uses `years > 5`
instead of `years >= 5`, so a customer with exactly 5 loyalty years gets
the tier-2 5% discount instead of 10%), write the smallest patch that
fixes the bug without changing behaviour on any other test. Show only
the diff.
```

AI's patch:

```diff
--- a/src/PriceEngine.java
+++ b/src/PriceEngine.java
@@ -54,7 +54,7 @@ public class PriceEngine {
         int years = customer.loyaltyYears();
         double loyaltyRate;
-        if (years > 5) {
+        if (years >= 5) {
             loyaltyRate = 0.10;
         } else if (years >= 3) {
             loyaltyRate = 0.05;
```

> This only changes the boundary at exactly 5 years — every other value of years (0-2, 3-4, 6+) evaluates identically before and after, so it shouldn't touch any other test's expected output.

Read it before applying - it's one character, and the only input that behaves differently is years == 5. Applied, ran `make test`: all 10 pass, including `quotesDiscountForLoyalCustomer`, nothing regressed.

Commit message: `fix: use >= 5 for loyalty tier-3 boundary`
Why: exact 5-year customers were falling into tier 2 because of a strict > check, off by one at the intended boundary.

## Part C - Reflect

The explain step was more useful than I expected, it pointed straight at line 57 instead of me having to scan the whole method myself. The patch step was basically just applying what the diagnosis already told us, so it was less about "thinking" and more a formality once root cause X was known. I don't think you need the whole stack trace for a plain assertion failure like this one, one or two lines (the assertion message plus the line number in the test file) already told the AI everything it needed. With just the first line it could only guess generically about "some assertion failed somewhere", it needed at least the test file frame to point at the actual line. Pasting the full trace didn't add anything here since the JUnit/reflection frames are just noise for this kind of bug, but for a real NullPointerException buried a few calls deep I'd expect the full trace to matter a lot more.

---

# Session 4B: Reducing cyclomatic complexity

## Setup

Didn't set up PMD/SpotBugs for this run (the Makefile only has JUnit, the plugins are still a TODO), so I counted complexity by hand and couldn't compare it against PMD's number. Rule I used: start at 1, +1 for every if / else-if / for / case, +1 for every && or ||.

`quote(Order, Customer)`:

| Part | Decision points |
|---|---|
| entry guard (order null / lines null / lines empty) - 1 if + 2 `\|\|` | 3 |
| customer null guard | 1 |
| for loop over lines | 1 |
| qty <= 0 skip | 1 |
| expedited surcharge | 1 |
| promo code null check | 1 |
| WELCOME10 / SUMMER15 / VIP20 - 3 ifs + 1 `&&` | 4 |
| loyalty ladder (>=5 / >=3 / >=1 / else) | 3 |
| loyaltyRate > 0 guard | 1 |
| region tax ladder (EU / US / IN / else) | 3 |
| taxRate > 0 guard | 1 |
| **Total** | **20** |

So CC = 20 + 1 = **21**. One ~75 line method doing validation, subtotal, surcharge, promo, loyalty and tax all together, well past the usual limit of 10.

## Part A - Rank refactor targets

Prompt (pasted with the whole file):

```
Rank the methods in this file by refactoring priority.
For each, give:
(a) current cyclomatic complexity estimate,
(b) the single refactor move you would apply first
    (extract-method, flatten-guard-clauses, replace-loop-
    with-pipeline, or rename),
(c) one sentence on the expected complexity reduction.
Do not propose code yet.
```

AI's ranking:

> PriceEngine has exactly one method, quote(Order, Customer), so there's nothing to rank against — it is the refactoring target by default.
>
> **quote(Order, Customer)**
> (a) current cyclomatic complexity estimate: ~20-21 (five sequential responsibilities, three separate if/else-if ladders, one loop, several compound boolean conditions)
> (b) refactor move: extract-method — pull each numbered step (subtotal, surcharge, promo, loyalty, tax) into its own private helper
> (c) expected reduction: extracting even one of the ladders (loyalty or tax) drops quote's own complexity by ~4-5 points immediately, and the extracted helper is trivial to test/read on its own since its complexity is isolated from the rest of the method.

Its estimate of 20-21 lines up with my count of 21.

## Part B - Apply one extract-method refactor

Went with the loyalty-tier block (lines 51-68 in the original file). It's self contained - only reads `subtotal`, `running` and `customer.loyaltyYears()`, and only writes `running`.

Prompt:

```
Extract the loyalty-tier discount block into a private
helper method `applyLoyaltyDiscount(...)`. Do NOT change
public method signatures. Show the diff.
```

Diff:

```diff
--- a/src/PriceEngine.java
+++ b/src/PriceEngine.java
@@ -48,22 +48,8 @@
         }

         // 4. Loyalty-tier discount
-        //    tier 1 (1-2 years):  2% off
-        //    tier 2 (3-4 years):  5% off
-        //    tier 3 (5+ years):  10% off
-        int years = customer.loyaltyYears();
-        double loyaltyRate;
-        if (years >= 5) {
-            loyaltyRate = 0.10;
-        } else if (years >= 3) {
-            loyaltyRate = 0.05;
-        } else if (years >= 1) {
-            loyaltyRate = 0.02;
-        } else {
-            loyaltyRate = 0.0;
-        }
-        if (loyaltyRate > 0.0) {
-            running = running.subtract(subtotal.times(loyaltyRate));
-        }
+        running = applyLoyaltyDiscount(running, subtotal, customer.loyaltyYears());

         // 5. Regional tax
         ...
         return running;
     }
+
+    /**
+     * tier 1 (1-2 years):  2% off
+     * tier 2 (3-4 years):  5% off
+     * tier 3 (5+ years):  10% off
+     */
+    private static Money applyLoyaltyDiscount(Money running, Money subtotal, int loyaltyYears) {
+        double loyaltyRate;
+        if (loyaltyYears >= 5) {
+            loyaltyRate = 0.10;
+        } else if (loyaltyYears >= 3) {
+            loyaltyRate = 0.05;
+        } else if (loyaltyYears >= 1) {
+            loyaltyRate = 0.02;
+        } else {
+            loyaltyRate = 0.0;
+        }
+        if (loyaltyRate > 0.0) {
+            return running.subtract(subtotal.times(loyaltyRate));
+        }
+        return running;
+    }
 }
```

Went through the three things from the handout before applying:

- **Types:** quote's signature and return type are unchanged. The helper takes `Money, Money, int` and returns `Money`, same types the old block used. It's `private static` so nothing new is public.
- **final modifiers:** the original block didn't have any (`years` and `loyaltyRate` were plain locals), so nothing got dropped.
- **Early returns:** the `return running` at the end of the helper is the same as the old "if rate is 0 do nothing" case, it doesn't skip anything in quote.

Applied, ran `make test` - all 10 still pass.

Commit message: `refactor: extract applyLoyaltyDiscount from quote`
Why: pulls the loyalty ladder out so quote reads as a list of steps instead of one long block, and the ladder can be read (and tested) on its own.

## Part D - Reflect

**How much did complexity drop?** quote went from **21 to 17**, so 4 lower. The loyalty ladder (3) plus the `loyaltyRate > 0` guard (1) moved out. The new helper `applyLoyaltyDiscount` is CC 5 on its own. Doing the same with the promo block and the tax block would get quote close to single digits.

**SpotBugs:** didn't do this part (plugin isn't in the Makefile yet), so nothing to say about rule vs AI explanation.

**Refactor I rejected:** turning the tax ladder (EU/US/IN/else) into a `Map<String, Double>` lookup. The AI suggested it to get the if/else-if chain down to one line. I didn't take it because the four tax rates won't change often, and swapping a readable ladder for a static map plus `getOrDefault` doesn't make the intent any clearer, it just moves the same four numbers somewhere else. It would also make it harder later if a rate ever depends on more than just the region string. Extract-method was worth it because it splits up unrelated jobs, the map idea was more about fewer lines than actually less complexity, so I kept the if/else-if.
