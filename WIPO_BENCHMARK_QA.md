# WIPO 2020 Annual Financial Report — Benchmark Q&A

Source: `wipo_pub_rn2021_18e-2.pdf` (WIPO Annual Financial Report and Financial Statements, Year to December 31, 2020).

20 question/answer pairs in the same shape as `RagBenchmark.QA` (query / expectedAnswer / keyPhrases). Drop into `RagBenchmark.kt` to benchmark the new document.

---

### Q1 — Surplus for the year
- **query:** What was WIPO's surplus for the year 2020?
- **expectedAnswer:** WIPO reported a surplus for the year of 135.9 million Swiss francs in 2020, compared to 97.7 million in 2019.
- **keyPhrases:** `135.9`, `surplus`, `Swiss francs`

### Q2 — Total revenue and expenses
- **query:** What were WIPO's total revenue and total expenses in 2020?
- **expectedAnswer:** Total revenue was 468.3 million Swiss francs and total expenses were 365.8 million Swiss francs.
- **keyPhrases:** `468.3`, `365.8`, `revenue`, `expenses`

### Q3 — Investment gains
- **query:** How much did WIPO achieve in investment gains in 2020?
- **expectedAnswer:** Investment gains totaled 33.4 million Swiss francs in 2020, compared to 42.1 million in 2019.
- **keyPhrases:** `33.4`, `investment`, `gains`

### Q4 — PCT share of revenue
- **query:** What share of total revenue did PCT system fees represent in 2020?
- **expectedAnswer:** PCT system fees accounted for 76.6% of total revenue, the largest single source.
- **keyPhrases:** `76.6`, `PCT`, `largest`

### Q5 — PCT revenue change
- **query:** How much did PCT system fee revenue change in 2020 versus 2019?
- **expectedAnswer:** PCT revenue rose 6.1% to 358.6 million Swiss francs (vs 338.1 million in 2019).
- **keyPhrases:** `358.6`, `6.1`, `PCT`

### Q6 — PCT applications filed
- **query:** How many PCT applications were filed in 2020 and how does that compare to 2019?
- **expectedAnswer:** Around 275,900 PCT applications were filed in 2020, a record figure and a 4.0% increase over 265,381 in 2019.
- **keyPhrases:** `275,900`, `4.0`, `record`

### Q7 — Madrid system change
- **query:** How did Madrid system revenue change in 2020?
- **expectedAnswer:** Madrid revenue fell 0.8% to 76.2 million Swiss francs; international trademark applications totaled about 63,800, the first decline since 2008-2009.
- **keyPhrases:** `76.2`, `0.8`, `Madrid`, `decline`

### Q8 — Hague system revenue
- **query:** What happened to Hague system revenue in 2020?
- **expectedAnswer:** Hague revenue increased 26.4% to 6.7 million Swiss francs (from 5.3 million in 2019), even though applications fell 1.7%.
- **keyPhrases:** `6.7`, `26.4`, `Hague`

### Q9 — Direct COVID-19 expenditure
- **query:** How much did WIPO spend directly on COVID-19 related items in 2020?
- **expectedAnswer:** Approximately 3.6 million Swiss francs, mainly for IT equipment and services to support remote and hybrid working.
- **keyPhrases:** `3.6`, `COVID`, `IT`

### Q10 — Travel cost collapse
- **query:** How did the cost of missions for staff and consultants change due to the pandemic?
- **expectedAnswer:** It fell from 5.7 million Swiss francs in 2019 to 0.5 million in 2020 because of travel bans and restrictions.
- **keyPhrases:** `5.7`, `0.5`, `missions`, `travel`

### Q11 — Largest expense category
- **query:** What was WIPO's largest expense category in 2020 and how big was it?
- **expectedAnswer:** Personnel expenditure was the largest expense at 233.7 million Swiss francs, representing 63.9% of total expenses.
- **keyPhrases:** `233.7`, `personnel`, `63.9`

### Q12 — Travel, training and grants drop
- **query:** By how much did travel, training and grants expenses fall in 2020?
- **expectedAnswer:** They fell 89.7%, from 17.5 million Swiss francs in 2019 to 1.8 million in 2020 — a direct consequence of the pandemic.
- **keyPhrases:** `89.7`, `17.5`, `1.8`, `travel`

### Q13 — Net assets and balance sheet size
- **query:** What were WIPO's net assets, total assets and total liabilities at the end of 2020?
- **expectedAnswer:** Net assets were 387.1 million, total assets 1,390.9 million and total liabilities 1,003.8 million Swiss francs.
- **keyPhrases:** `387.1`, `1,390.9`, `1,003.8`

### Q14 — Cash and investments
- **query:** What was WIPO's combined cash, cash equivalents and investment balance at year-end 2020?
- **expectedAnswer:** 932.0 million Swiss francs, 177.9 million higher than the 754.1 million at the end of 2019, and equal to 67.0% of total assets.
- **keyPhrases:** `932.0`, `754.1`, `67.0`

### Q15 — ASHI liability
- **query:** What was the ASHI (After-Service Health Insurance) liability and what share of employee benefits did it represent?
- **expectedAnswer:** The ASHI liability was 452.8 million Swiss francs, or 91.4% of total employee benefit liabilities of 495.3 million.
- **keyPhrases:** `452.8`, `91.4`, `ASHI`

### Q16 — Discount rate change
- **query:** How did the discount rate used for the ASHI liability change in 2020?
- **expectedAnswer:** It was lowered from 0.50% to 0.30%, contributing to the increase in the ASHI liability.
- **keyPhrases:** `0.50`, `0.30`, `discount`

### Q17 — Voluntary contributions
- **query:** How much did voluntary contribution revenue change in 2020 and why?
- **expectedAnswer:** Voluntary contribution revenue fell 46.8% to 5.8 million Swiss francs because, under Special Accounts, revenue is recognized as expense is incurred and pandemic-related delays lowered expenditure.
- **keyPhrases:** `5.8`, `46.8`, `voluntary`

### Q18 — Director General change
- **query:** Who became WIPO's Director General in 2020 and when did the term start?
- **expectedAnswer:** Mr Daren Tang was appointed on May 8, 2020 and began his six-year term on October 1, 2020, succeeding Mr Francis Gurry.
- **keyPhrases:** `Daren Tang`, `October 1`, `six-year`

### Q19 — Member States and mandate
- **query:** How many Member States does WIPO have and what is its role?
- **expectedAnswer:** WIPO is a specialized agency of the United Nations with 193 Member States; it is the global forum for intellectual property services, policy, information and cooperation.
- **keyPhrases:** `193`, `Member States`, `United Nations`

### Q20 — Productivity of IP systems during COVID
- **query:** What productivity level did WIPO's PCT, Madrid and Hague systems maintain by December 2020?
- **expectedAnswer:** Productivity indicators for the PCT, Madrid and Hague systems were all at 98% or above by December 2020, despite the pandemic.
- **keyPhrases:** `98`, `PCT`, `Madrid`, `Hague`

---

## Kotlin snippet (paste into `RagBenchmark.kt`)

```kotlin
QA(
    id = "q1_surplus_2020",
    query = "What was WIPO's surplus for the year 2020?",
    expectedAnswer = "Surplus of 135.9 million Swiss francs in 2020.",
    keyPhrases = listOf("135.9", "surplus", "Swiss francs")
),
QA(
    id = "q2_revenue_expenses",
    query = "What were WIPO's total revenue and total expenses in 2020?",
    expectedAnswer = "Revenue 468.3 million, expenses 365.8 million Swiss francs.",
    keyPhrases = listOf("468.3", "365.8", "revenue", "expenses")
),
QA(
    id = "q3_investment_gains",
    query = "How much did WIPO achieve in investment gains in 2020?",
    expectedAnswer = "Investment gains of 33.4 million Swiss francs.",
    keyPhrases = listOf("33.4", "investment", "gains")
),
QA(
    id = "q4_pct_share",
    query = "What share of total revenue did PCT system fees represent in 2020?",
    expectedAnswer = "PCT fees accounted for 76.6% of total revenue.",
    keyPhrases = listOf("76.6", "PCT", "largest")
),
QA(
    id = "q5_pct_revenue",
    query = "How much did PCT system fee revenue change in 2020 versus 2019?",
    expectedAnswer = "Rose 6.1% to 358.6 million Swiss francs.",
    keyPhrases = listOf("358.6", "6.1", "PCT")
),
QA(
    id = "q6_pct_filings",
    query = "How many PCT applications were filed in 2020?",
    expectedAnswer = "~275,900 applications, a record, +4.0% over 265,381 in 2019.",
    keyPhrases = listOf("275,900", "4.0", "record")
),
QA(
    id = "q7_madrid",
    query = "How did Madrid system revenue change in 2020?",
    expectedAnswer = "Fell 0.8% to 76.2 million Swiss francs; first decline since 2008-2009.",
    keyPhrases = listOf("76.2", "0.8", "Madrid")
),
QA(
    id = "q8_hague",
    query = "What happened to Hague system revenue in 2020?",
    expectedAnswer = "Increased 26.4% to 6.7 million Swiss francs.",
    keyPhrases = listOf("6.7", "26.4", "Hague")
),
QA(
    id = "q9_covid_spend",
    query = "How much did WIPO spend directly on COVID-19 related items in 2020?",
    expectedAnswer = "Approximately 3.6 million Swiss francs, mainly IT equipment and services.",
    keyPhrases = listOf("3.6", "COVID", "IT")
),
QA(
    id = "q10_missions",
    query = "How did the cost of missions for staff and consultants change?",
    expectedAnswer = "Fell from 5.7 to 0.5 million Swiss francs due to travel bans.",
    keyPhrases = listOf("5.7", "0.5", "missions")
),
QA(
    id = "q11_personnel",
    query = "What was WIPO's largest expense in 2020 and how big was it?",
    expectedAnswer = "Personnel expenditure of 233.7 million Swiss francs, 63.9% of total expenses.",
    keyPhrases = listOf("233.7", "personnel", "63.9")
),
QA(
    id = "q12_travel_training",
    query = "By how much did travel, training and grants expenses fall in 2020?",
    expectedAnswer = "Fell 89.7%, from 17.5 to 1.8 million Swiss francs.",
    keyPhrases = listOf("89.7", "17.5", "1.8")
),
QA(
    id = "q13_balance_sheet",
    query = "What were WIPO's net assets, total assets and total liabilities at year-end 2020?",
    expectedAnswer = "Net assets 387.1M; total assets 1,390.9M; total liabilities 1,003.8M Swiss francs.",
    keyPhrases = listOf("387.1", "1,390.9", "1,003.8")
),
QA(
    id = "q14_cash",
    query = "What was WIPO's combined cash and investment balance at year-end 2020?",
    expectedAnswer = "932.0 million Swiss francs, +177.9 million vs 2019; 67.0% of total assets.",
    keyPhrases = listOf("932.0", "754.1", "67.0")
),
QA(
    id = "q15_ashi",
    query = "What was the ASHI liability and what share of employee benefits did it represent?",
    expectedAnswer = "452.8 million Swiss francs, 91.4% of total employee benefit liabilities.",
    keyPhrases = listOf("452.8", "91.4", "ASHI")
),
QA(
    id = "q16_discount_rate",
    query = "How did the discount rate used for the ASHI liability change in 2020?",
    expectedAnswer = "Lowered from 0.50% to 0.30%.",
    keyPhrases = listOf("0.50", "0.30", "discount")
),
QA(
    id = "q17_voluntary",
    query = "How much did voluntary contribution revenue change in 2020 and why?",
    expectedAnswer = "Fell 46.8% to 5.8 million Swiss francs because expenses recognised were lower.",
    keyPhrases = listOf("5.8", "46.8", "voluntary")
),
QA(
    id = "q18_dg",
    query = "Who became WIPO's Director General in 2020 and when?",
    expectedAnswer = "Daren Tang, appointed May 8, 2020, six-year term starting October 1, 2020.",
    keyPhrases = listOf("Daren Tang", "October 1", "six-year")
),
QA(
    id = "q19_member_states",
    query = "How many Member States does WIPO have and what is its role?",
    expectedAnswer = "193 Member States; UN specialized agency, global forum for IP services.",
    keyPhrases = listOf("193", "Member States", "United Nations")
),
QA(
    id = "q20_productivity",
    query = "What productivity level did WIPO's PCT, Madrid and Hague systems maintain by December 2020?",
    expectedAnswer = "All at 98% or above by December 2020.",
    keyPhrases = listOf("98", "PCT", "Madrid", "Hague")
)
```
