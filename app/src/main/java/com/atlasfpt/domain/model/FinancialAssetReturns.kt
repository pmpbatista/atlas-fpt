package com.atlasfpt.domain.model

/**
 * Computed FIFO returns for a financial asset.
 *
 * Realized P&L is summed across each SELL by matching it against the oldest unconsumed BUY
 * lots. Unrealized P&L is the mark-to-market on the surviving BUY pool. Total return divides
 * by the gross capital deployed (sum of BUY notionals), which is the most intuitive base for
 * a "how did my investment do" question even if some of that capital has been recycled.
 *
 * `xirr` is annualised internal rate of return over actual cash flows (BUYs as negative,
 * SELLs as positive, plus a terminal mark-to-market flow when there's still a position).
 */
data class FinancialAssetReturns(
    val realizedPnl: Double,
    val dividendIncome: Double,
    val unrealizedPnl: Double?,
    val totalReturn: Double?,
    val totalReturnPct: Double?,
    val totalInvested: Double,
    val xirr: Double?,
    val netQuantity: Double,
    val avgCostPerRemainingShare: Double,
)
